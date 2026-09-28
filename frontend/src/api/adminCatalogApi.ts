import {apiRequest} from './client'

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

export interface CompanyInput {
    name: string
    industry: string
    description: string
    websiteUrl: string
    location: string
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

export interface JobPostingInput {
    companyId?: number
    title: string
    jobRole: string
    employmentType: EmploymentType
    location: string
    description: string
    sourceUrl: string
    opensAt: string | null
    closesAt: string | null
    manuallyClosed: boolean
}

function queryPath(path: string, keyword: string, page: number, size = 20): string {
    const params = new URLSearchParams({page: String(page), size: String(size)})
    if (keyword.trim()) {
        params.set('keyword', keyword.trim())
    }
    return `${path}?${params.toString()}`
}

export function listCompanies(keyword = '', page = 0): Promise<Page<CompanySummary>> {
    return apiRequest(queryPath('/api/companies', keyword, page), {authenticated: true})
}

export async function listAllCompanies(): Promise<CompanySummary[]> {
    const firstPage = await apiRequest<Page<CompanySummary>>(
        queryPath('/api/companies', '', 0, 100),
        {authenticated: true},
    )
    if (firstPage.totalPages <= 1) return firstPage.items

    const remainingPages = await Promise.all(
        Array.from({length: firstPage.totalPages - 1}, (_, index) =>
            apiRequest<Page<CompanySummary>>(
                queryPath('/api/companies', '', index + 1, 100),
                {authenticated: true},
            ),
        ),
    )
    return [...firstPage.items, ...remainingPages.flatMap((page) => page.items)]
}

export function listJobPostings(keyword = '', page = 0): Promise<Page<JobPostingSummary>> {
    return apiRequest(queryPath('/api/job-postings', keyword, page), {authenticated: true})
}

export function getCompany(id: number): Promise<CompanyDetails> {
    return apiRequest(`/api/companies/${id}`, {authenticated: true})
}

export function getJobPosting(id: number): Promise<JobPostingDetails> {
    return apiRequest(`/api/job-postings/${id}`, {authenticated: true})
}

export function createCompany(input: CompanyInput): Promise<CompanyDetails> {
    return apiRequest('/api/admin/companies', {method: 'POST', authenticated: true, json: input})
}

export function updateCompany(id: number, input: CompanyInput): Promise<CompanyDetails> {
    return apiRequest(`/api/admin/companies/${id}`, {method: 'PUT', authenticated: true, json: input})
}

export function deleteCompany(id: number): Promise<void> {
    return apiRequest(`/api/admin/companies/${id}`, {method: 'DELETE', authenticated: true})
}

export function createJobPosting(input: JobPostingInput & { companyId: number }): Promise<JobPostingDetails> {
    return apiRequest('/api/admin/job-postings', {method: 'POST', authenticated: true, json: input})
}

export function updateJobPosting(id: number, input: JobPostingInput): Promise<JobPostingDetails> {
    const updateInput = {...input}
    delete updateInput.companyId
    return apiRequest(`/api/admin/job-postings/${id}`, {method: 'PUT', authenticated: true, json: updateInput})
}

export function deleteJobPosting(id: number): Promise<void> {
    return apiRequest(`/api/admin/job-postings/${id}`, {method: 'DELETE', authenticated: true})
}
