import {apiRequest} from "./client";

export type AiUsageFeature =
    | 'INITIAL_QUESTIONS'
    | 'FOLLOW_UP'
    | 'ANSWER_EVALUATION'
    | 'COVER_LETTER_DRAFT'

export interface AiFeatureUsage {
    feature: AiUsageFeature
    limit: number
    used: number
    remaining: number
}

interface UsagePeriod {
    timeZone: string
    usageDate: string
    resetAt: string
}

export type AiUsage = UsagePeriod & (
    | {
    enabled: false
    chatAdmissionEnabled: boolean
    active: null
    activeLimit: null
    features: AiFeatureUsage[]
}
    | {
    enabled: true
    chatAdmissionEnabled: boolean
    active: number
    activeLimit: number
    features: AiFeatureUsage[]
}
    )

export function getAiUsage(signal?: AbortSignal): Promise<AiUsage> {
    return apiRequest('/api/ai/usage', {
        authenticated: true,
        cache: 'no-store',
        signal,
    })
}

export const aiUsageLabels: Record<AiUsageFeature, string> = {
    INITIAL_QUESTIONS: '초기 면접 질문',
    FOLLOW_UP: '꼬리 질문',
    ANSWER_EVALUATION: '답변 평가',
    COVER_LETTER_DRAFT: '자기소개서 초안',
}