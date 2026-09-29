import {afterEach, describe, expect, it, vi} from 'vitest'
import {createInterviewSession, getInterviewSession, type InterviewSession,} from './interviewSessionApi'

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
})
