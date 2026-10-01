import {apiRequest} from "./client.ts";
import type {CoverLetterDetails} from "./documentsApi";

export type DraftStatus =
    | 'PENDING'
    | 'RUNNING'
    | 'REVIEW_READY'
    | 'FAILED'
    | 'APPLIED'

export interface DraftSummary {
    id: number
    sourceDraftId: number | null
    jobPostingId: number
    resumeId: number
    baseVersionNumber: number
    status: DraftStatus
    generatedTitle: string | null
    changeSummary: string | null
    warnings: string[]
    failureCode: string | null
    appliedVersionNumber: number | null
    createdAt: string
    updatedAt: string
    generatedAt: string | null
    appliedAt: string | null
}

export interface DraftDetails extends DraftSummary {
    coverLetterId: number
    inputHash: string
    instruction: string | null
    generatedContent: string | null
    attemptCount: number
}

export interface CreateDraftInput {
    jobPostingId: number
    resumeId?: number
    instruction?: string
}

export interface ApplyDraftInput {
    title: string
    content: string
}

const authenticated = {authenticated: true} as const

function draftPath(coverLetterId: number): string {
    return `/api/cover-letters/${coverLetterId}/drafts`
}

export function listDrafts(coverLetterId: number): Promise<DraftSummary[]> {
    return apiRequest(draftPath(coverLetterId), authenticated)
}

export function getDraft(coverLetterId: number, draftId: number): Promise<DraftDetails> {
    return apiRequest(`${draftPath(coverLetterId)}/${draftId}`, authenticated)
}

export function createDraft(coverLetterId: number, input: CreateDraftInput): Promise<DraftDetails> {
    const instruction = input.instruction?.trim()

    return apiRequest(draftPath(coverLetterId), {
        ...authenticated,
        method: 'POST',
        json: {
            jobPostingId: input.jobPostingId,
            ...(input.resumeId === undefined ? {} : {resumeId: input.resumeId}),
            ...(instruction ? {instruction} : {}),
        },
    })
}

export function regenerateDraft(coverLetterId: number, draftId: number): Promise<DraftDetails> {
    return apiRequest(`${draftPath(coverLetterId)}/${draftId}/regenerate`, {
        ...authenticated,
        method: 'POST',
    })
}

export function applyDraft(coverLetterId: number, draftId: number, input: ApplyDraftInput): Promise<CoverLetterDetails> {
    return apiRequest(`${draftPath(coverLetterId)}/${draftId}/apply`, {
        ...authenticated,
        method: 'POST',
        json: {
            title: input.title.trim(),
            content: input.content.trim(),
        },
    })
}