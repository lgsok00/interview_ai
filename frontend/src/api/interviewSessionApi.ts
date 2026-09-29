import {apiRequest} from "./client";

export type InterviewSessionStatus =
    | 'GENERATING'
    | 'READY'
    | 'IN_PROGRESS'
    | 'COMPLETED'
    | 'FAILED'

export interface InterviewSession {
    id: number
    jobPostingId: number
    status: InterviewSessionStatus
    companyName: string
    jobPostingTitle: string
    jobRole: string
    failureCode: string | null
    createdAt: string
    updatedAt: string
    completedAt: string | null
}

export function createInterviewSession(jobPostingId: number): Promise<InterviewSession> {
    return apiRequest('/api/interview-sessions', {
        method: 'POST',
        authenticated: true,
        json: {jobPostingId},
    })
}

export function getInterviewSession(sessionId: number): Promise<InterviewSession> {
    return apiRequest(`/api/interview-sessions/${sessionId}`, {
        authenticated: true
    })
}