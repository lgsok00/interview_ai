import {afterEach, describe, expect, it, vi} from 'vitest'
import {ApiError} from './ApiError'
import {
    applyDraft,
    createDraft,
    type DraftDetails,
    type DraftStatus,
    type DraftSummary,
    getDraft,
    listDrafts,
    regenerateDraft,
} from './coverLetterDraftApi'
import type {CoverLetterDetails} from './documentsApi'

const {apiRequestMock} = vi.hoisted(() => ({apiRequestMock: vi.fn()}))

vi.mock('./client', () => ({apiRequest: apiRequestMock}))

function draftFixture(overrides: Partial<DraftDetails> = {}): DraftDetails {
    return {
        id: 41,
        coverLetterId: 17,
        sourceDraftId: null,
        jobPostingId: 9,
        resumeId: 29,
        baseVersionNumber: 3,
        inputHash: 'a'.repeat(64),
        status: 'REVIEW_READY',
        instruction: '문제 해결 경험 강조',
        generatedTitle: '문제 해결 경험',
        generatedContent: '이력서에 있는 경험을 바탕으로 작성한 초안',
        changeSummary: '지원 직무와 경험을 연결했습니다.',
        warnings: ['성과 수치를 확인해 주세요.'],
        failureCode: null,
        attemptCount: 1,
        appliedVersionNumber: null,
        createdAt: '2026-10-01T10:00:00',
        updatedAt: '2026-10-01T10:00:10',
        generatedAt: '2026-10-01T10:00:10',
        appliedAt: null,
        ...overrides,
    }
}

describe('coverLetterDraftApi', () => {
    afterEach(() => apiRequestMock.mockReset())

    it('소유 자기소개서의 초안 목록을 인증 조회하고 상태·버전·실패 정보를 전달한다', async () => {
        const states: DraftStatus[] = ['PENDING', 'RUNNING', 'REVIEW_READY', 'FAILED', 'APPLIED']
        const summaries: DraftSummary[] = states.map((status, index) => ({
            id: 41 + index,
            sourceDraftId: null,
            jobPostingId: 9,
            resumeId: 29,
            baseVersionNumber: 3,
            status,
            generatedTitle: status === 'REVIEW_READY' || status === 'APPLIED' ? '생성 제목' : null,
            changeSummary: null,
            warnings: [],
            failureCode: status === 'FAILED' ? 'DRAFT_AI_NOT_CONFIGURED' : null,
            appliedVersionNumber: status === 'APPLIED' ? 4 : null,
            createdAt: '2026-10-01T10:00:00',
            updatedAt: '2026-10-01T10:00:10',
            generatedAt: null,
            appliedAt: null,
        }))
        apiRequestMock.mockResolvedValue(summaries)

        await expect(listDrafts(17)).resolves.toEqual(summaries)
        expect(apiRequestMock).toHaveBeenCalledExactlyOnceWith('/api/cover-letters/17/drafts', {
            authenticated: true,
        })
    })

    it('초안이 없는 자기소개서의 빈 목록을 그대로 전달한다', async () => {
        apiRequestMock.mockResolvedValue([])

        await expect(listDrafts(17)).resolves.toEqual([])
    })

    it('상세 조회는 대상 문서와 초안 ID를 사용하고 생성 본문·경고를 보존한다', async () => {
        const draft = draftFixture()
        apiRequestMock.mockResolvedValue(draft)

        await expect(getDraft(17, 41)).resolves.toEqual(draft)
        expect(apiRequestMock).toHaveBeenCalledExactlyOnceWith('/api/cover-letters/17/drafts/41', {
            authenticated: true,
        })
    })

    it('생성 요청은 필수 공고만 전송하고 선택 이력서·추가 지시를 생략한다', async () => {
        const pending = draftFixture({
            status: 'PENDING',
            instruction: null,
            generatedTitle: null,
            generatedContent: null,
            changeSummary: null,
            warnings: [],
            attemptCount: 0,
            generatedAt: null,
        })
        apiRequestMock.mockResolvedValue(pending)

        await expect(createDraft(17, {jobPostingId: 9})).resolves.toEqual(pending)
        expect(apiRequestMock).toHaveBeenCalledExactlyOnceWith('/api/cover-letters/17/drafts', {
            authenticated: true,
            method: 'POST',
            json: {jobPostingId: 9},
        })
    })

    it('선택한 이력서와 앞뒤 공백을 제거한 추가 지시를 JSON으로 전송한다', async () => {
        apiRequestMock.mockResolvedValue(draftFixture())

        await createDraft(17, {
            jobPostingId: 9,
            resumeId: 29,
            instruction: '  문제 해결 경험 강조\n',
        })

        expect(apiRequestMock).toHaveBeenCalledExactlyOnceWith('/api/cover-letters/17/drafts', {
            authenticated: true,
            method: 'POST',
            json: {
                jobPostingId: 9,
                resumeId: 29,
                instruction: '문제 해결 경험 강조',
            },
        })
    })

    it.each(['', '   ', '\n\t'])('빈 추가 지시 %j는 서버에 빈 문자열로 보내지 않는다', async (instruction) => {
        apiRequestMock.mockResolvedValue(draftFixture())

        await createDraft(17, {jobPostingId: 9, instruction})

        expect(apiRequestMock).toHaveBeenCalledExactlyOnceWith('/api/cover-letters/17/drafts', {
            authenticated: true,
            method: 'POST',
            json: {jobPostingId: 9},
        })
    })

    it.each([1, 1000])('추가 지시 %i자 경계값을 잘라내지 않고 전송한다', async (length) => {
        const instruction = '가'.repeat(length)
        apiRequestMock.mockResolvedValue(draftFixture())

        await createDraft(17, {jobPostingId: 9, instruction})

        expect(apiRequestMock).toHaveBeenCalledWith('/api/cover-letters/17/drafts', {
            authenticated: true,
            method: 'POST',
            json: {jobPostingId: 9, instruction},
        })
    })

    it('AI 미설정으로 생성 응답이 FAILED여도 실패 코드를 포함한 초안을 반환한다', async () => {
        const failed = draftFixture({
            status: 'FAILED',
            failureCode: 'DRAFT_AI_NOT_CONFIGURED',
            generatedTitle: null,
            generatedContent: null,
            generatedAt: null,
            warnings: [],
            attemptCount: 0,
        })
        apiRequestMock.mockResolvedValue(failed)

        await expect(createDraft(17, {jobPostingId: 9})).resolves.toEqual(failed)
        expect(apiRequestMock).toHaveBeenCalledTimes(1)
    })

    it('재생성은 기존 초안 endpoint에 본문 없이 POST하고 새 초안 ID를 반환한다', async () => {
        const regenerated = draftFixture({id: 52, sourceDraftId: 41, baseVersionNumber: 4, status: 'PENDING'})
        apiRequestMock.mockResolvedValue(regenerated)

        await expect(regenerateDraft(17, 41)).resolves.toEqual(regenerated)
        expect(apiRequestMock).toHaveBeenCalledExactlyOnceWith('/api/cover-letters/17/drafts/41/regenerate', {
            authenticated: true,
            method: 'POST',
        })
    })

    it('AI 원문 대신 사용자가 검토 수정한 제목·본문을 적용하고 새 문서 버전을 반환한다', async () => {
        const letter: CoverLetterDetails = {
            id: 17,
            title: '검토한 제목',
            content: '수치를 확인하고 수정한 내용\n두 번째 문단',
            currentVersionNumber: 4,
            representative: true,
            createdAt: '2026-10-01T09:00:00',
            updatedAt: '2026-10-01T10:05:00',
        }
        apiRequestMock.mockResolvedValue(letter)

        await expect(applyDraft(17, 41, {
            title: '  검토한 제목  ',
            content: '\n수치를 확인하고 수정한 내용\n두 번째 문단\n',
        })).resolves.toEqual(letter)
        expect(apiRequestMock).toHaveBeenCalledExactlyOnceWith('/api/cover-letters/17/drafts/41/apply', {
            authenticated: true,
            method: 'POST',
            json: {title: letter.title, content: letter.content},
        })
    })

    it.each([
        {titleLength: 1, contentLength: 1},
        {titleLength: 100, contentLength: 20000},
    ])('적용 제목 $titleLength자·본문 $contentLength자 경계값을 보존한다', async ({titleLength, contentLength}) => {
        const input = {title: '제'.repeat(titleLength), content: '본'.repeat(contentLength)}
        apiRequestMock.mockResolvedValue({id: 17, currentVersionNumber: 4})

        await applyDraft(17, 41, input)

        expect(apiRequestMock).toHaveBeenCalledExactlyOnceWith('/api/cover-letters/17/drafts/41/apply', {
            authenticated: true,
            method: 'POST',
            json: input,
        })
    })

    it.each([
        {label: '목록 인증 실패', status: 401, code: 'UNAUTHORIZED', request: () => listDrafts(17)},
        {label: '타인·없는 초안 조회', status: 404, code: 'COVER_LETTER_DRAFT_NOT_FOUND', request: () => getDraft(17, 999)},
        {
            label: '대표 이력서 미준비',
            status: 409,
            code: 'REPRESENTATIVE_RESUME_NOT_READY',
            request: () => createDraft(17, {jobPostingId: 9})
        },
        {
            label: '추가 지시 길이 초과',
            status: 400,
            code: 'VALIDATION_FAILED',
            request: () => createDraft(17, {jobPostingId: 9, instruction: '가'.repeat(1001)})
        },
        {
            label: '생성 중 재생성 거부',
            status: 409,
            code: 'COVER_LETTER_DRAFT_CONFLICT',
            request: () => regenerateDraft(17, 41)
        },
        {
            label: '기준 버전 충돌',
            status: 409,
            code: 'DRAFT_BASE_VERSION_CONFLICT',
            request: () => applyDraft(17, 41, {title: '제목', content: '내용'})
        },
        {
            label: '적용 상태 충돌',
            status: 409,
            code: 'COVER_LETTER_DRAFT_CONFLICT',
            request: () => applyDraft(17, 41, {title: '제목', content: '내용'})
        },
        {label: '서버 장애', status: 503, code: 'SERVICE_UNAVAILABLE', request: () => getDraft(17, 41)},
    ])('$label 오류와 필드 정보를 보존하고 자동 재요청하지 않는다', async ({status, code, request}) => {
        const failure = new ApiError(status, '요청을 처리할 수 없습니다.', code, {
            instruction: '추가 지시를 확인해 주세요.',
        })
        apiRequestMock.mockRejectedValue(failure)

        await expect(request()).rejects.toBe(failure)
        expect(apiRequestMock).toHaveBeenCalledTimes(1)
    })
})
