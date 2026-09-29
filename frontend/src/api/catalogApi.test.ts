import {afterEach, describe, expect, it, vi} from 'vitest'
import {
    getCompany,
    getJobPosting,
    listCompanies,
    listCompanyJobPostings,
    listFavoriteCompanies,
    listJobPostings,
    setCompanyFavorite,
} from './catalogApi'

const {apiRequestMock} = vi.hoisted(() => ({apiRequestMock: vi.fn()}))

vi.mock('./client', () => ({apiRequest: apiRequestMock}))

describe('catalogApi', () => {
    afterEach(() => apiRequestMock.mockReset())

    it('기업 검색어를 정리하고 page·size를 인증된 목록 요청에 전달한다', async () => {
        apiRequestMock.mockResolvedValue({
            items: [],
            page: 2,
            size: 100,
            totalElements: 0,
            totalPages: 0,
        })

        await listCompanies({keyword: '  테스트 기업  ', page: 2, size: 100})

        expect(apiRequestMock).toHaveBeenCalledWith(
            '/api/companies?keyword=%ED%85%8C%EC%8A%A4%ED%8A%B8+%EA%B8%B0%EC%97%85&page=2&size=100',
            {authenticated: true},
        )
    })

    it('기본 기업 목록과 관심 기업 목록은 첫 페이지 크기 20을 사용한다', async () => {
        apiRequestMock.mockResolvedValue({items: [], page: 0, size: 20, totalElements: 0, totalPages: 0})

        await listCompanies()
        await listFavoriteCompanies()

        expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/companies?page=0&size=20', {authenticated: true})
        expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/companies/favorites?page=0&size=20', {authenticated: true})
    })

    it('모집 상태와 선택한 기업 ID를 공고 검색 조건으로 전달한다', async () => {
        apiRequestMock.mockResolvedValue({items: [], page: 1, size: 20, totalElements: 0, totalPages: 0})

        await listJobPostings({keyword: '개발자', status: 'OPEN', companyId: 42, page: 1})

        expect(apiRequestMock).toHaveBeenCalledWith(
            '/api/job-postings?keyword=%EA%B0%9C%EB%B0%9C%EC%9E%90&status=OPEN&companyId=42&page=1&size=20',
            {authenticated: true},
        )
    })

    it('기업별 공고 목록은 기업 ID와 검색·상태·페이지 조건을 경로와 query에 전달한다', async () => {
        apiRequestMock.mockResolvedValue({items: [], page: 0, size: 5, totalElements: 0, totalPages: 0})

        await listCompanyJobPostings(42, {keyword: '백엔드', status: 'CLOSED', page: 0, size: 5})

        expect(apiRequestMock).toHaveBeenCalledWith(
            '/api/companies/42/job-postings?keyword=%EB%B0%B1%EC%97%94%EB%93%9C&status=CLOSED&page=0&size=5',
            {authenticated: true},
        )
    })

    it('기업 및 공고 상세 조회를 인증된 API에 위임한다', async () => {
        apiRequestMock.mockResolvedValueOnce({id: 7, name: '테스트 기업'})
            .mockResolvedValueOnce({id: 9, title: '테스트 공고'})

        await expect(getCompany(7)).resolves.toMatchObject({id: 7})
        await expect(getJobPosting(9)).resolves.toMatchObject({id: 9})

        expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/companies/7', {authenticated: true})
        expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/job-postings/9', {authenticated: true})
    })

    it('관심 기업 등록과 해제를 각각 PUT·DELETE로 요청한다', async () => {
        apiRequestMock.mockResolvedValue(undefined)

        await setCompanyFavorite(7, true)
        await setCompanyFavorite(7, false)

        expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/companies/7/favorite', {
            method: 'PUT', authenticated: true,
        })
        expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/companies/7/favorite', {
            method: 'DELETE', authenticated: true,
        })
    })

    it('API 오류를 호출자에게 그대로 전달한다', async () => {
        const error = new Error('COMPANY_NOT_FOUND')
        apiRequestMock.mockRejectedValue(error)

        await expect(getCompany(999)).rejects.toBe(error)
    })
})
