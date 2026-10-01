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

export interface InterviewSessionPageResponse {
    content: InterviewSession[]
    page: number
    size: number
    totalElements: number
    totalPages: number
    first: boolean
    last: boolean
}

export function createInterviewSession(jobPostingId: number): Promise<InterviewSession> {
    return apiRequest('/api/interview-sessions', {
        method: 'POST',
        authenticated: true,
        json: {jobPostingId},
    })
}

export function listInterviewSessions(page = 0, size = 20): Promise<InterviewSessionPageResponse> {
    const query = new URLSearchParams({page: String(page), size: String(size)})

    return apiRequest(`/api/interview-sessions?${query}`, {
        authenticated: true
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

export type InterviewAnalysisStatus = 'PENDING' | 'PARTIAL' | 'COMPLETED'
export type InterviewEvaluationStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface InterviewResultEvaluation {
    status: InterviewEvaluationStatus | null
    starScore: number | null
    logicScore: number | null
    jobFitScore: number | null
    averageScore: number | null
    strengths: string | null
    improvements: string | null
    improvedAnswer: string | null
    failureCode: string | null
    completedAt: string | null
}

export interface InterviewResultQuestion {
    questionId: number
    parentQuestionId: number | null
    questionType: InterviewQuestionType
    sequence: number
    question: string
    answerId: number | null
    answer: string | null
    evaluation: InterviewResultEvaluation | null
}

export interface InterviewResult {
    session: {
        id: number
        companyName: string
        jobPostingTitle: string
        jobRole: string
        completedAt: string
    }
    analysisStatus: InterviewAnalysisStatus
    answerCount: number
    completedEvaluationCount: number
    pendingEvaluationCount: number
    processingEvaluationCount: number
    failedEvaluationCount: number
    notRequestedEvaluationCount: number
    overall: {
        sampleCount: number
        averageScore: number | null
        starScore: number | null
        logicScore: number | null
        jobFitScore: number | null
    }
    byQuestionType: Array<{
        questionType: InterviewQuestionType
        answerCount: number
        evaluatedCount: number
        averageScore: number | null
        starScore: number | null
        logicScore: number | null
        jobFitScore: number | null
    }>
    questions: InterviewResultQuestion[]
}

export function getInterviewResult(sessionId: number): Promise<InterviewResult> {
    return apiRequest(`/api/interview-sessions/${sessionId}/result`, {
        authenticated: true
    })
}