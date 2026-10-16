import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {accessTokenStore} from '../auth/accessTokenStore'
import {ApiError} from './ApiError'
import type {AiUsage} from './aiUsageApi'
import {getAiUsage} from './aiUsageApi'

vi.mock('../config/env', () => ({env: {apiBaseUrl: 'https://interview.test'}}))

const usage: AiUsage = {
    enabled: true,
    chatAdmissionEnabled: true,
    timeZone: 'Asia/Seoul',
    usageDate: '2026-10-16',
    resetAt: '2026-10-17T00:00:00+09:00',
    active: 1,
    activeLimit: 2,
    features: [
        {feature: 'INITIAL_QUESTIONS', limit: 3, used: 1, remaining: 2},
        {feature: 'FOLLOW_UP', limit: 15, used: 0, remaining: 15},
        {feature: 'ANSWER_EVALUATION', limit: 30, used: 0, remaining: 30},
        {feature: 'COVER_LETTER_DRAFT', limit: 3, used: 3, remaining: 0},
    ],
}

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: {'Content-Type': 'application/json'},
    })
}

describe('AI 사용량 API와 공통 HTTP client', () => {
    const fetchMock = vi.fn<typeof fetch>()

    beforeEach(() => {
        fetchMock.mockReset()
        vi.stubGlobal('fetch', fetchMock)
        accessTokenStore.set('access-token')
    })

    afterEach(() => {
        accessTokenStore.clear()
        vi.unstubAllGlobals()
    })

    it('본인 조회 GET에 인증·cookie·캐시 금지를 전달하고 본문은 보내지 않는다', async () => {
        fetchMock.mockResolvedValueOnce(json(usage))
        await expect(getAiUsage()).resolves.toEqual(usage)
        const [url, options] = fetchMock.mock.calls[0]!
        expect(url).toBe('https://interview.test/api/ai/usage')
        expect(options?.method ?? 'GET').toBe('GET')
        expect(options?.credentials).toBe('include')
        expect(options?.cache).toBe('no-store')
        expect(options?.body).toBeUndefined()
        expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer access-token')
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it('요청 취소 signal을 그대로 전달한다', async () => {
        const controller = new AbortController()
        fetchMock.mockResolvedValueOnce(json(usage))
        await getAiUsage(controller.signal)
        expect(fetchMock.mock.calls[0]![1]?.signal).toBe(controller.signal)
    })

    it('제한 비활성화의 null·빈 목록을 보존한다', async () => {
        const disabled: AiUsage = {
            ...usage, enabled: false, active: null, activeLimit: null, features: [],
        }
        fetchMock.mockResolvedValueOnce(json(disabled))
        await expect(getAiUsage()).resolves.toEqual(disabled)
    })

    it('접수 중단·한도 소진·초과 집계를 변경하지 않는다', async () => {
        const stopped = {...usage, chatAdmissionEnabled: false, active: 4}
        fetchMock.mockResolvedValueOnce(json(stopped))
        await expect(getAiUsage()).resolves.toEqual(stopped)
    })

    it.each([
        [403, 'USER_SUSPENDED'],
        [404, 'USER_NOT_FOUND'],
        [429, 'AI_DAILY_LIMIT_EXCEEDED'],
        [503, 'AI_USAGE_UNAVAILABLE'],
        [503, 'AI_CAPACITY_EXCEEDED'],
    ] as const)('%i %s는 재발급·재전송 없이 오류와 인증을 보존한다', async (status, code) => {
        fetchMock.mockResolvedValueOnce(json({code, message: '서버 안내', errors: {}}, status))
        await expect(getAiUsage()).rejects.toMatchObject({status, code, message: '서버 안내'})
        expect(fetchMock).toHaveBeenCalledTimes(1)
        expect(accessTokenStore.get()).toBe('access-token')
    })

    it('401 후 인증을 갱신하고 캐시 금지·signal을 유지해 한 번 재조회한다', async () => {
        const controller = new AbortController()
        fetchMock.mockResolvedValueOnce(json({code: 'INVALID_ACCESS_TOKEN', message: '만료', errors: {}}, 401))
            .mockResolvedValueOnce(json({accessToken: 'renewed-token'}))
            .mockResolvedValueOnce(json(usage))
        await expect(getAiUsage(controller.signal)).resolves.toEqual(usage)
        expect(fetchMock).toHaveBeenCalledTimes(3)
        expect(fetchMock.mock.calls[1]![0]).toBe('https://interview.test/api/auth/refresh')
        const [url, options] = fetchMock.mock.calls[2]!
        expect(url).toBe('https://interview.test/api/ai/usage')
        expect(options?.signal).toBe(controller.signal)
        expect(options?.cache).toBe('no-store')
        expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer renewed-token')
    })

    it('재발급 실패 시 조회를 반복하지 않고 인증을 정리한다', async () => {
        fetchMock.mockResolvedValueOnce(json({code: 'INVALID_ACCESS_TOKEN', message: '만료', errors: {}}, 401))
            .mockResolvedValueOnce(json({code: 'INVALID_REFRESH_TOKEN', message: '만료', errors: {}}, 401))
        await expect(getAiUsage()).rejects.toMatchObject({status: 401})
        expect(fetchMock).toHaveBeenCalledTimes(2)
        expect(accessTokenStore.get()).toBeNull()
    })

    it.each(['network', 'abort'])('%s 실패를 0회 사용량으로 대체하지 않는다', async kind => {
        const error = kind === 'abort'
            ? new DOMException('취소', 'AbortError')
            : new TypeError('통신 실패')
        fetchMock.mockRejectedValueOnce(error)
        await expect(getAiUsage()).rejects.toBe(error)
        expect(fetchMock).toHaveBeenCalledTimes(1)
        expect(accessTokenStore.get()).toBe('access-token')
    })

    it('잘못된 JSON은 오류로 전달한다', async () => {
        fetchMock.mockResolvedValueOnce(new Response('{', {
            headers: {'Content-Type': 'application/json'},
        }))
        await expect(getAiUsage()).rejects.toBeInstanceOf(ApiError)
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it('JSON이 아닌 장애 응답도 재전송하지 않는다', async () => {
        fetchMock.mockResolvedValueOnce(new Response('점검 중', {status: 503}))
        await expect(getAiUsage()).rejects.toMatchObject({status: 503, message: '점검 중'})
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })
})
