import {afterEach, describe, expect, it, vi} from 'vitest'
import {
    createInterviewSession,
    getInterviewQuestions,
    getInterviewSession,
    type InterviewQuestion,
    type InterviewSession,
    startInterviewSession,
} from './interviewSessionApi'

const {apiRequestMock} = vi.hoisted(() => ({apiRequestMock: vi.fn()}))

vi.mock('./client', () => ({apiRequest: apiRequestMock}))

const generatingSession: InterviewSession = {
    id: 31,
    jobPostingId: 17,
    status: 'GENERATING',
    companyName: '테스트 기업',
    jobPostingTitle: '백엔드 개발자',
    jobRole: '백엔드',
    failureCode: null,
    createdAt: '2026-09-29T10:00:00Z',
    updatedAt: '2026-09-29T10:00:00Z',
    completedAt: null,
}

const questions: InterviewQuestion[] = [
    {
        id: 51,
        sequenceNumber: 1,
        questionType: 'TECHNICAL',
        generationSource: 'AI',
        content: '서비스의 장애를 진단하고 복구한 경험을 설명해 주세요.',
        createdAt: '2026-09-29T10:01:00Z',
    },
]

describe('interviewSessionApi', () => {
    afterEach(() => apiRequestMock.mockReset())

    it('공고 ID를 포함한 인증된 세션 생성 요청을 보내고 생성 결과를 반환한다', async () => {
        apiRequestMock.mockResolvedValue(generatingSession)

        await expect(createInterviewSession(17)).resolves.toEqual(generatingSession)

        expect(apiRequestMock).toHaveBeenCalledWith('/api/interview-sessions', {
            method: 'POST',
            authenticated: true,
            json: {jobPostingId: 17},
        })
    })

    it('세션 생성 API 오류를 호출자에게 전달한다', async () => {
        const error = new Error('JOB_POSTING_NOT_FOUND')
        apiRequestMock.mockRejectedValue(error)

        await expect(createInterviewSession(999)).rejects.toBe(error)
    })

    it('생성 중인 세션 상태를 인증된 상세 endpoint에서 조회한다', async () => {
        apiRequestMock.mockResolvedValue(generatingSession)

        await expect(getInterviewSession(31)).resolves.toMatchObject({
            id: 31,
            status: 'GENERATING',
        })

        expect(apiRequestMock).toHaveBeenCalledWith('/api/interview-sessions/31', {
            authenticated: true,
        })
    })

    it('질문 생성 완료 상태를 그대로 반환한다', async () => {
        const readySession = {...generatingSession, status: 'READY' as const}
        apiRequestMock.mockResolvedValue(readySession)

        await expect(getInterviewSession(31)).resolves.toMatchObject({
            id: 31,
            status: 'READY',
        })
    })

    it('세션 상태 조회 오류를 호출자에게 전달한다', async () => {
        const error = new Error('INTERVIEW_SESSION_NOT_FOUND')
        apiRequestMock.mockRejectedValue(error)

        await expect(getInterviewSession(999)).rejects.toBe(error)
    })

    it('세션 질문을 인증된 질문 endpoint에서 조회한다', async () => {
        apiRequestMock.mockResolvedValue(questions)

        await expect(getInterviewQuestions(31)).resolves.toEqual(questions)

        expect(apiRequestMock).toHaveBeenCalledWith('/api/interview-sessions/31/questions', {
            authenticated: true,
        })
    })

    it('질문 조회 오류를 호출자에게 전달한다', async () => {
        const error = new Error('INTERVIEW_SESSION_NOT_FOUND')
        apiRequestMock.mockRejectedValue(error)

        await expect(getInterviewQuestions(999)).rejects.toBe(error)
    })

    it('인증된 면접 시작 POST 요청을 보내고 변경된 세션을 반환한다', async () => {
        const startedSession = {...generatingSession, status: 'IN_PROGRESS' as const}
        apiRequestMock.mockResolvedValue(startedSession)

        await expect(startInterviewSession(31)).resolves.toEqual(startedSession)

        expect(apiRequestMock).toHaveBeenCalledWith('/api/interview-sessions/31/start', {
            method: 'POST',
            authenticated: true,
        })
    })

    it('면접 시작 오류를 호출자에게 전달한다', async () => {
        const error = new Error('INTERVIEW_SESSION_CONFLICT')
        apiRequestMock.mockRejectedValue(error)

        await expect(startInterviewSession(31)).rejects.toBe(error)
    })
})
