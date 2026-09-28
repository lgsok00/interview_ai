import {afterEach, describe, expect, it, vi} from 'vitest'
import {
    type CompanyInput,
    createCompany,
    createJobPosting,
    deleteCompany,
    deleteJobPosting,
    type JobPostingInput,
    listAllCompanies,
    listCompanies,
    listJobPostings,
    updateCompany,
    updateJobPosting,
} from './adminCatalogApi'

const {apiRequestMock} = vi.hoisted(() => ({apiRequestMock: vi.fn()}))

vi.mock('./client', () => ({apiRequest: apiRequestMock}))

const companyInput: CompanyInput = {
    name: '테스트 기업',
    industry: 'IT',
    description: '기업 소개',
    websiteUrl: 'https://example.com',
    location: '서울',
}

const postingInput: JobPostingInput = {
    companyId: 12,
    title: '프론트엔드 개발자',
    jobRole: '프론트엔드',
    employmentType: 'FULL_TIME',
    location: '서울',
    description: '공고 본문',
    sourceUrl: 'https://example.com/jobs/1',
    opensAt: '2026-09-01T00:00:00Z',
    closesAt: null,
    manuallyClosed: false,
}

describe('adminCatalogApi', () => {
    afterEach(() => apiRequestMock.mockReset())

    it('검색어와 페이지를 전달해 인증된 기업·공고 목록을 가져온다', async () => {
        apiRequestMock.mockResolvedValue({items: [], page: 0, size: 20, totalElements: 0, totalPages: 0})

        await listCompanies('테스트 기업', 2)
        await listJobPostings('프론트엔드', 1)

        expect(apiRequestMock).toHaveBeenNthCalledWith(
            1,
            '/api/companies?page=2&size=20&keyword=%ED%85%8C%EC%8A%A4%ED%8A%B8+%EA%B8%B0%EC%97%85',
            {authenticated: true},
        )
        expect(apiRequestMock).toHaveBeenNthCalledWith(
            2,
            '/api/job-postings?page=1&size=20&keyword=%ED%94%84%EB%A1%A0%ED%8A%B8%EC%97%94%EB%93%9C',
            {authenticated: true},
        )
    })

    it('공고의 기업 선택을 위해 기업 페이지를 모두 불러온다', async () => {
        apiRequestMock
            .mockResolvedValueOnce({items: [{id: 1}], page: 0, size: 100, totalElements: 101, totalPages: 2})
            .mockResolvedValueOnce({items: [{id: 101}], page: 1, size: 100, totalElements: 101, totalPages: 2})

        await expect(listAllCompanies()).resolves.toEqual([{id: 1}, {id: 101}])
        expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/companies?page=0&size=100', {authenticated: true})
        expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/companies?page=1&size=100', {authenticated: true})
    })

    it('기업 등록과 수정은 관리자 경로에 인증된 JSON을 보낸다', async () => {
        apiRequestMock.mockResolvedValue({id: 4})

        await createCompany(companyInput)
        await updateCompany(4, companyInput)

        expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/admin/companies', {
            method: 'POST', authenticated: true, json: companyInput,
        })
        expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/admin/companies/4', {
            method: 'PUT', authenticated: true, json: companyInput,
        })
    })

    it('공고 생성에는 companyId를 포함하고 수정에서는 제외한다', async () => {
        apiRequestMock.mockResolvedValue({id: 9})

        await createJobPosting({...postingInput, companyId: 12})
        await updateJobPosting(9, postingInput)

        expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/admin/job-postings', {
            method: 'POST', authenticated: true, json: {...postingInput, companyId: 12},
        })
        const updatePayload = {...postingInput}
        delete updatePayload.companyId
        expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/admin/job-postings/9', {
            method: 'PUT', authenticated: true, json: updatePayload,
        })
    })

    it('기업·공고 삭제를 관리자 endpoint에 위임하고 실패는 호출자에게 전달한다', async () => {
        const error = new Error('COMPANY_HAS_JOB_POSTINGS')
        apiRequestMock.mockResolvedValueOnce(undefined).mockRejectedValueOnce(error)

        await expect(deleteCompany(4)).resolves.toBeUndefined()
        await expect(deleteJobPosting(9)).rejects.toBe(error)

        expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/admin/companies/4', {
            method: 'DELETE', authenticated: true,
        })
        expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/admin/job-postings/9', {
            method: 'DELETE', authenticated: true,
        })
    })
})
