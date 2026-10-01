import {afterEach, describe, expect, it, vi} from 'vitest'
import {
    createCoverLetter,
    deleteCoverLetter,
    deleteResume,
    downloadResume,
    getCoverLetter,
    listCoverLetters,
    listCoverLetterVersions,
    listResumes,
    restoreCoverLetterVersion,
    setRepresentativeCoverLetter,
    setRepresentativeResume,
    updateCoverLetter,
    updateResumeTitle,
    uploadResume,
} from './documentsApi'

const {apiRequestMock, apiRequestBlobMock} = vi.hoisted(() => ({
    apiRequestMock: vi.fn(),
    apiRequestBlobMock: vi.fn(),
}))

vi.mock('./client', () => ({
    apiRequest: apiRequestMock,
    apiRequestBlob: apiRequestBlobMock,
}))

describe('documentsApi', () => {
    afterEach(() => {
        apiRequestMock.mockReset()
        apiRequestBlobMock.mockReset()
    })

    it('자기소개서 목록·상세와 버전 목록을 인증된 API에 요청한다', async () => {
        apiRequestMock.mockResolvedValue([])

        await listCoverLetters()
        await getCoverLetter(17)
        await listCoverLetterVersions(17)

        expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/cover-letters', {authenticated: true})
        expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/cover-letters/17', {authenticated: true})
        expect(apiRequestMock).toHaveBeenNthCalledWith(3, '/api/cover-letters/17/versions', {authenticated: true})
    })

    it('자기소개서 생성·수정은 제목과 본문을 JSON으로 전달한다', async () => {
        apiRequestMock.mockResolvedValue({id: 17})
        const input = {title: '경험 정리', content: '문제 해결 경험'}

        await createCoverLetter(input)
        await updateCoverLetter(17, input)

        expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/cover-letters', {
            authenticated: true,
            method: 'POST',
            json: input,
        })
        expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/cover-letters/17', {
            authenticated: true,
            method: 'PUT',
            json: input,
        })
    })

    it('자기소개서 최대 제목·본문 길이 경계값을 변경하지 않고 전달한다', async () => {
        apiRequestMock.mockResolvedValue({id: 18})
        const input = {title: '가'.repeat(100), content: '나'.repeat(20000)}

        await createCoverLetter(input)

        expect(apiRequestMock).toHaveBeenCalledWith('/api/cover-letters', {
            authenticated: true,
            method: 'POST',
            json: input,
        })
    })

    it('자기소개서 삭제·대표 설정·버전 복원을 올바른 HTTP method와 경로로 요청한다', async () => {
        apiRequestMock.mockResolvedValue(undefined)

        await deleteCoverLetter(17)
        await setRepresentativeCoverLetter(17)
        await restoreCoverLetterVersion(17, 3)

        expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/cover-letters/17', {
            authenticated: true,
            method: 'DELETE',
        })
        expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/cover-letters/17/representative', {
            authenticated: true,
            method: 'PUT',
        })
        expect(apiRequestMock).toHaveBeenNthCalledWith(3, '/api/cover-letters/17/versions/3/restore', {
            authenticated: true,
            method: 'POST',
        })
    })

    it('이력서 목록과 제목 변경을 인증된 API에 전달한다', async () => {
        apiRequestMock.mockResolvedValue([])

        await listResumes()
        await updateResumeTitle(29, '백엔드 이력서')

        expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/resumes', {authenticated: true})
        expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/resumes/29', {
            authenticated: true,
            method: 'PUT',
            json: {title: '백엔드 이력서'},
        })
    })

    it('이력서 업로드는 metadata JSON part와 파일을 multipart body로 전송한다', async () => {
        apiRequestMock.mockResolvedValue({id: 29})
        const file = new File(['pdf-content'], 'resume.pdf', {type: 'application/pdf'})

        await uploadResume('백엔드 이력서', file)

        const [, options] = apiRequestMock.mock.calls[0] as [string, { body: FormData; [key: string]: unknown }]
        const metadata = options.body.get('metadata')
        expect(apiRequestMock).toHaveBeenCalledWith('/api/resumes', expect.objectContaining({
            authenticated: true,
            method: 'POST',
        }))
        expect(options.body).toBeInstanceOf(FormData)
        expect(metadata).toBeInstanceOf(Blob)
        if (!(metadata instanceof Blob)) throw new Error('metadata multipart part must be a Blob')
        expect(await metadata.text()).toBe(JSON.stringify({title: '백엔드 이력서'}))
        expect(options.body.get('file')).toBe(file)
    })

    it('이력서 삭제·대표 설정·PDF 다운로드를 해당 endpoint로 위임한다', async () => {
        apiRequestMock.mockResolvedValue(undefined)
        const pdf = new Blob(['pdf-content'], {type: 'application/pdf'})
        apiRequestBlobMock.mockResolvedValue(pdf)

        await deleteResume(29)
        await setRepresentativeResume(29)
        await expect(downloadResume(29)).resolves.toBe(pdf)

        expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/resumes/29', {
            authenticated: true,
            method: 'DELETE',
        })
        expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/resumes/29/representative', {
            authenticated: true,
            method: 'PUT',
        })
        expect(apiRequestBlobMock).toHaveBeenCalledWith('/api/resumes/29/file')
    })

    it('백엔드 API 오류를 호출자에게 그대로 전달한다', async () => {
        const failure = new Error('문서를 찾을 수 없습니다.')
        apiRequestMock.mockRejectedValue(failure)

        await expect(getCoverLetter(999)).rejects.toBe(failure)
    })
})
