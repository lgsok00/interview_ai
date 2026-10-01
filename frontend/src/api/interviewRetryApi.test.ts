import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {accessTokenStore} from '../auth/accessTokenStore'
import {ApiError} from './ApiError'
import {
    type AnswerEvaluationResponse,
    retryInterviewAnswerEvaluation,
    retryInterviewGeneration,
} from './interviewSessionApi'

vi.mock('../config/env', () => ({env: {apiBaseUrl: 'https://interview.test'}}))

const pendingEvaluation: AnswerEvaluationResponse = {
    id: 91,
    answerId: 71,
    questionId: 51,
    status: 'PENDING',
    starScore: null,
    logicScore: null,
    jobFitScore: null,
    strengths: null,
    improvements: null,
    improvedAnswer: null,
    attemptCount: 0,
    manualRetryCount: 2,
    failureCode: null,
    createdAt: '2026-10-01T10:00:00',
    updatedAt: '2026-10-01T10:01:00',
    completedAt: null,
}

function jsonResponse(body: unknown, status: number): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: {'Content-Type': 'application/json'},
    })
}

const endpoints = [
    {
        name: '질문 생성',
        path: '/api/interview-sessions/31/generation/retry',
        retry: () => retryInterviewGeneration(31),
        accepted: () => new Response(null, {status: 202}),
    },
    {
        name: '답변 평가',
        path: '/api/interview-sessions/31/answers/71/evaluation/retry',
        retry: () => retryInterviewAnswerEvaluation(31, 71),
        accepted: () => jsonResponse(pendingEvaluation, 202),
    },
]

describe('면접 재시도 API와 공통 HTTP client', () => {
    const fetchMock = vi.fn<typeof fetch>()

    beforeEach(() => {
        fetchMock.mockReset()
        vi.stubGlobal('fetch', fetchMock)
        accessTokenStore.set('test-access-token')
    })

    afterEach(() => {
        accessTokenStore.clear()
        vi.unstubAllGlobals()
    })

    it('질문 재시도의 빈 202 응답을 JSON 파싱 오류 없이 처리한다', async () => {
        fetchMock.mockResolvedValueOnce(new Response(null, {status: 202}))

        await expect(retryInterviewGeneration(31)).resolves.toBeUndefined()
        expect(fetchMock).toHaveBeenCalledTimes(1)
        const [url, init] = fetchMock.mock.calls[0]!
        expect(url).toBe('https://interview.test/api/interview-sessions/31/generation/retry')
        expect(init).toMatchObject({method: 'POST', credentials: 'include'})
        expect(init?.body).toBeUndefined()
        expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer test-access-token')
    })

    it('평가 재시도의 마지막 허용 횟수 응답과 PENDING·초기화된 평가 정보를 보존한다', async () => {
        fetchMock.mockResolvedValueOnce(jsonResponse(pendingEvaluation, 202))

        await expect(retryInterviewAnswerEvaluation(31, 71)).resolves.toEqual(pendingEvaluation)
        expect(fetchMock).toHaveBeenCalledTimes(1)
        const [url, init] = fetchMock.mock.calls[0]!
        expect(url).toBe('https://interview.test/api/interview-sessions/31/answers/71/evaluation/retry')
        expect(init).toMatchObject({method: 'POST', credentials: 'include'})
        expect(init?.body).toBeUndefined()
        expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer test-access-token')
    })

    it.each([
        {
            name: '질문 소유권', retry: endpoints[0]!.retry, status: 404,
            code: 'INTERVIEW_SESSION_NOT_FOUND', message: '면접 세션을 찾을 수 없습니다.'
        },
        {
            name: '질문 상태 충돌', retry: endpoints[0]!.retry, status: 409,
            code: 'INTERVIEW_GENERATION_CONFLICT', message: '실패한 면접 세션만 재시도할 수 있습니다.'
        },
        {
            name: '질문 재시도 한도', retry: endpoints[0]!.retry, status: 409,
            code: 'INTERVIEW_GENERATION_CONFLICT', message: '재시도 한도를 초과했거나 재시도 가능한 작업이 없습니다.'
        },
        {
            name: '질문 서버 장애', retry: endpoints[0]!.retry, status: 500,
            code: 'INTERNAL_SERVER_ERROR', message: '서버 오류가 발생했습니다.'
        },
        {
            name: '평가 소유권', retry: endpoints[1]!.retry, status: 404,
            code: 'INTERVIEW_SESSION_NOT_FOUND', message: '면접 세션을 찾을 수 없습니다.'
        },
        {
            name: '평가 없음', retry: endpoints[1]!.retry, status: 404,
            code: 'ANSWER_EVALUATION_NOT_FOUND', message: '답변 평가를 찾을 수 없습니다.'
        },
        {
            name: '평가 상태 충돌', retry: endpoints[1]!.retry, status: 409,
            code: 'ANSWER_EVALUATION_RETRY_CONFLICT', message: '실패한 평가만 다시 요청할 수 있습니다.'
        },
        {
            name: '평가 재시도 한도', retry: endpoints[1]!.retry, status: 409,
            code: 'ANSWER_EVALUATION_RETRY_CONFLICT', message: '수동 재시도 횟수를 초과했습니다.'
        },
        {
            name: '평가 비활성화', retry: endpoints[1]!.retry, status: 503,
            code: 'ANSWER_EVALUATION_DISABLED', message: '답변 평가 작업이 비활성화되어 있습니다.'
        },
    ])('$name: 서버 오류 정보와 메시지를 유지하고 POST를 반복하지 않는다', async (scenario) => {
        fetchMock.mockResolvedValueOnce(jsonResponse({
            code: scenario.code,
            message: scenario.message,
            errors: {},
        }, scenario.status))

        const error = await scenario.retry().catch((reason: unknown) => reason)
        expect(error).toBeInstanceOf(ApiError)
        expect(error).toMatchObject({
            status: scenario.status,
            code: scenario.code,
            message: scenario.message,
            errors: {},
        })
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it.each(endpoints)('$name: 네트워크 실패를 전달하고 자동으로 POST를 재전송하지 않는다', async ({retry}) => {
        const error = new TypeError('Failed to fetch')
        fetchMock.mockRejectedValueOnce(error)

        await expect(retry()).rejects.toBe(error)
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it.each(endpoints)('$name: 401 뒤 토큰을 재발급해 동일한 재시도 endpoint를 한 번 다시 호출한다', async ({retry, path, accepted}) => {
        fetchMock
            .mockResolvedValueOnce(new Response(null, {status: 401}))
            .mockResolvedValueOnce(jsonResponse({accessToken: 'renewed-token'}, 200))
            .mockResolvedValueOnce(accepted())

        await retry()

        expect(fetchMock).toHaveBeenCalledTimes(3)
        expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
            `https://interview.test${path}`,
            'https://interview.test/api/auth/refresh',
            `https://interview.test${path}`,
        ])
        const retryInit = fetchMock.mock.calls[2]![1]
        expect(retryInit).toMatchObject({method: 'POST', credentials: 'include'})
        expect(retryInit?.body).toBeUndefined()
        expect(new Headers(retryInit?.headers).get('Authorization')).toBe('Bearer renewed-token')
        expect(accessTokenStore.get()).toBe('renewed-token')
    })

    it.each(endpoints)('$name: 재발급 실패 시 인증 오류를 반환하고 원 POST를 재전송하지 않는다', async ({retry}) => {
        fetchMock
            .mockResolvedValueOnce(jsonResponse({code: 'UNAUTHORIZED', message: '인증이 필요합니다.', errors: {}}, 401))
            .mockResolvedValueOnce(jsonResponse({
                code: 'INVALID_REFRESH_TOKEN',
                message: '재로그인이 필요합니다.',
                errors: {}
            }, 401))

        await expect(retry()).rejects.toMatchObject({status: 401, code: 'UNAUTHORIZED'})
        expect(fetchMock).toHaveBeenCalledTimes(2)
        expect(fetchMock.mock.calls[1]![0]).toBe('https://interview.test/api/auth/refresh')
        expect(accessTokenStore.get()).toBeNull()
    })

    it('평가 접수 응답이 잘못된 JSON이면 성공으로 처리하지 않고 오류를 전달한다', async () => {
        fetchMock.mockResolvedValueOnce(new Response('{invalid-json', {
            status: 202,
            headers: {'Content-Type': 'application/json'},
        }))

        await expect(retryInterviewAnswerEvaluation(31, 71)).rejects.toMatchObject({
            status: 202,
            message: '서버가 올바르지 않은 JSON 응답을 반환했습니다.',
        })
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })
})
