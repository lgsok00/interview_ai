import {apiRequest} from "./client";

export interface Page<T> {
    items: T[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

export interface CompanySummary {
    id: number
    name: string
    industry: string | null
    location: string | null
    favorite: boolean
}

export interface CompanyDetails extends CompanySummary {
    description: string
    websiteUrl: string | null
}

export type EmploymentType = 'FULL_TIME' | 'CONTRACT' | 'INTERN' | 'PART_TIME' | 'OTHER'
export type JobPostingStatus = 'SCHEDULED' | 'OPEN' | 'CLOSED'

export interface JobPostingSummary {
    id: number
    companyId: number
    companyName: string
    title: string
    jobRole: string
    employmentType: EmploymentType
    location: string | null
    status: JobPostingStatus
    opensAt: string | null
    closesAt: string | null
}

export interface JobPostingDetails extends JobPostingSummary {
    description: string
    sourceUrl: string | null
    manuallyClosed: boolean
}

export interface CatalogQuery {
    keyword?: string
    page?: number
    size?: number
    status?: JobPostingStatus
    companyId?: number
}

function toQuery(params: Record<string, string | number | undefined>): string {
    const query = new URLSearchParams()

    for (const [key, value] of Object.entries(params)) {
        if (value !== undefined && String(value).trim() !== '') {
            query.set(key, String(value))
        }
    }

    return query.toString()
}

function get<T>(path: string): Promise<T> {
    return apiRequest(path, {authenticated: true})
}

export function listCompanies(query: CatalogQuery = {}): Promise<Page<CompanySummary>> {
    const suffix = toQuery({
        keyword: query.keyword?.trim(),
        page: query.page ?? 0,
        size: query.size ?? 20,
    })

    return get(`/api/companies?${suffix}`)
}

export function listFavoriteCompanies(query: Pick<CatalogQuery, 'page' | 'size'> = {}): Promise<Page<CompanySummary>> {
    const suffix = toQuery({
        page: query.page ?? 0,
        size: query.size ?? 20,
    })

    return get(`/api/companies/favorites?${suffix}`)
}

export function getCompany(id: number): Promise<CompanyDetails> {
    return get(`/api/companies/${id}`)
}

export function setCompanyFavorite(id: number, favorite: boolean): Promise<void> {
    return apiRequest(`/api/companies/${id}/favorite`, {
        method: favorite ? 'PUT' : 'DELETE',
        authenticated: true,
    })
}

export function listJobPostings(query: CatalogQuery = {}): Promise<Page<JobPostingSummary>> {
    const suffix = toQuery({
        keyword: query.keyword?.trim(),
        status: query.status,
        companyId: query.companyId,
        page: query.page ?? 0,
        size: query.size ?? 20,
    })

    return get(`/api/job-postings?${suffix}`)
}

export function listCompanyJobPostings(companyId: number, query: CatalogQuery = {}): Promise<Page<JobPostingSummary>> {
    const suffix = toQuery({
        keyword: query.keyword?.trim(),
        status: query.status,
        page: query.page ?? 0,
        size: query.size ?? 20,
    })

    return get(`/api/companies/${companyId}/job-postings?${suffix}`)
}

export function getJobPosting(id: number): Promise<JobPostingDetails> {
    return get(`/api/job-postings/${id}`)
}