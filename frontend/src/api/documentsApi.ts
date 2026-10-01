import {apiRequest, apiRequestBlob} from "./client";

export interface CoverLetterSummary {
    id: number
    title: string
    currentVersionNumber: number
    representative: boolean
    createdAt: string
    updatedAt: string
}

export interface CoverLetterDetails extends CoverLetterSummary {
    content: string
}

export interface CoverLetterVersion {
    versionNumber: number
    title: string
    current: boolean
    createdAt: string
}

export interface ResumeSummary {
    id: number
    title: string
    originalFilename: string
    fileSize: number
    extractionStatus: 'PENDING' | 'COMPLETED' | 'FAILED'
    representative: boolean
    createdAt: string
    updatedAt: string
}

const authenticated = {authenticated: true} as const

export function listCoverLetters(): Promise<CoverLetterSummary[]> {
    return apiRequest('/api/cover-letters', authenticated)
}

export function getCoverLetter(id: number): Promise<CoverLetterDetails> {
    return apiRequest(`/api/cover-letters/${id}`, authenticated)
}

export function createCoverLetter(input: { title: string; content: string }): Promise<CoverLetterDetails> {
    return apiRequest('/api/cover-letters', {
        ...authenticated,
        method: 'POST',
        json: input,
    })
}

export function updateCoverLetter(id: number, input: { title: string; content: string }): Promise<CoverLetterDetails> {
    return apiRequest(`/api/cover-letters/${id}`, {
        ...authenticated,
        method: 'PUT',
        json: input,
    })
}

export function deleteCoverLetter(id: number): Promise<void> {
    return apiRequest(`/api/cover-letters/${id}`, {
        ...authenticated,
        method: 'DELETE',
    })
}

export function setRepresentativeCoverLetter(id: number): Promise<void> {
    return apiRequest(`/api/cover-letters/${id}/representative`, {
        ...authenticated,
        method: 'PUT',
    })
}

export function listCoverLetterVersions(id: number): Promise<CoverLetterVersion[]> {
    return apiRequest(`/api/cover-letters/${id}/versions`, authenticated)
}

export function restoreCoverLetterVersion(id: number, versionNumber: number): Promise<CoverLetterDetails> {
    return apiRequest(`/api/cover-letters/${id}/versions/${versionNumber}/restore`, {
        ...authenticated,
        method: 'POST',
    })
}

export function listResumes(): Promise<ResumeSummary[]> {
    return apiRequest('/api/resumes', authenticated)
}

export function uploadResume(title: string, file: File): Promise<ResumeSummary> {
    const body = new FormData()
    body.append('metadata', new Blob([JSON.stringify({title})], {type: 'application/json'}))
    body.append('file', file)

    return apiRequest('/api/resumes', {
        ...authenticated,
        method: 'POST',
        body,
    })
}

export function updateResumeTitle(id: number, title: string): Promise<ResumeSummary> {
    return apiRequest(`/api/resumes/${id}`, {
        ...authenticated,
        method: 'PUT',
        json: {title},
    })
}

export function deleteResume(id: number): Promise<void> {
    return apiRequest(`/api/resumes/${id}`, {
        ...authenticated,
        method: 'DELETE',
    })
}

export function setRepresentativeResume(id: number): Promise<void> {
    return apiRequest(`/api/resumes/${id}/representative`, {
        ...authenticated,
        method: 'PUT',
    })
}

export function downloadResume(id: number): Promise<Blob> {
    return apiRequestBlob(`/api/resumes/${id}/file`)
}