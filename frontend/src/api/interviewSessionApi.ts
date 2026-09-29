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

export type InterviewQuestionType = 'TECHNICAL' | 'BEHAVIORAL' | 'FOLLOW_UP'
export type QuestionGenerationSource = 'AI' | 'FALLBACK'

export interface InterviewQuestion {
    id: number
    sequenceNumber: number
    questionType: InterviewQuestionType
    generationSource: QuestionGenerationSource
    content: string
    createdAt: string
}

export function getInterviewQuestions(sessionId: number): Promise<InterviewQuestion[]> {
    return apiRequest(`/api/interview-sessions/${sessionId}/questions`, {
        authenticated: true
    })
}

export function startInterviewSession(sessionId: number): Promise<InterviewSession> {
    return apiRequest(`/api/interview-sessions/${sessionId}/start`, {
        method: 'POST',
        authenticated: true
    })
}

export interface InterviewAnswer {
    id: number
    questionId: number
    content: string
    followUpQuestionId: number | null
    createdAt: string
}

export interface InterviewFollowUp {
    parentQuestionId: number
    question: InterviewQuestion
}

export function getInterviewAnswers(sessionId: number): Promise<InterviewAnswer[]> {
    return apiRequest(`/api/interview-sessions/${sessionId}/answers`, {
        authenticated: true
    })
}

export function submitInterviewAnswer(
    sessionId: number,
    questionId: number,
    content: string,
): Promise<InterviewAnswer> {
    return apiRequest(`/api/interview-sessions/${sessionId}/questions/${questionId}/answer`, {
        method: 'PUT',
        authenticated: true,
        json: {content},
    })
}

export function generateInterviewFollowUp(sessionId: number, questionId: number): Promise<InterviewFollowUp> {
    return apiRequest(`/api/interview-sessions/${sessionId}/questions/${questionId}/follow-up`, {
        method: 'POST',
        authenticated: true,
    })
}

export function completeInterviewSession(sessionId: number): Promise<InterviewSession> {
    return apiRequest(`/api/interview-sessions/${sessionId}/complete`, {
        method: 'POST',
        authenticated: true,
    })
}