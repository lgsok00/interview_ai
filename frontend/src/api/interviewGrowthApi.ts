import type {InterviewQuestionType} from "./interviewSessionApi";
import {apiRequest} from "./client";

export interface InterviewGrowthAnalysis {
    period: {
        from: string;
        to: string;
    };
    filters: {
        jobRole: string | null;
        companyId: number | null;
        jobPostingId: number | null;
    };
    summary: {
        sessionCount: number;
        evaluatedAnswerCount: number;
        excludedSessionCount: number;
        averageScore: number | null;
        starScore: number | null;
        logicScore: number | null;
        jobFitScore: number | null;
    };
    byQuestionType: Array<{
        questionType: InterviewQuestionType;
        evaluatedCount: number;
        averageScore: number | null;
        starScore: number | null;
        logicScore: number | null;
        jobFitScore: number | null;
    }>;
    trends: Array<{
        sessionId: number;
        completedAt: string;
        companyName: string;
        jobRole: string;
        evaluatedAnswerCount: number;
        averageScore: number | null;
        starScore: number | null;
        logicScore: number | null;
        jobFitScore: number | null;
    }>;
    change: {
        basis: string;
        recentSessionCount: number;
        previousSessionCount: number;
        averageScore: number | null;
        starScore: number | null;
        logicScore: number | null;
        jobFitScore: number | null;
    } | null;
    strengths: Array<{
        dimension: string;
        questionType: InterviewQuestionType;
        score: number;
        sampleCount: number;
        reason: string;
    }>;
    weaknesses: Array<{
        dimension: string;
        questionType: InterviewQuestionType;
        score: number;
        sampleCount: number;
        reason: string;
    }>;
    learningRoadmap: Array<{
        priority: number;
        dimension: string;
        questionType: InterviewQuestionType;
        currentScore: number;
        targetScore: number;
        title: string;
        actions: string[];
        evidence: {
            sampleCount: number;
            relatedSessionIds: number[];
        };
    }>;
    insufficientData: boolean;
    minimumEvaluatedAnswerCount: number;
}

export interface InterviewGrowthAnalysisFilters {
    from: string;
    to: string;
    jobRole?: string;
    companyId?: number;
    jobPostingId?: number;
}

export function getInterviewGrowthAnalysis(filters: InterviewGrowthAnalysisFilters,): Promise<InterviewGrowthAnalysis> {
    const params = new URLSearchParams({
        from: filters.from,
        to: filters.to,
    });

    const jobRole = filters.jobRole?.trim();

    if (jobRole) params.set("jobRole", jobRole);

    if (filters.companyId !== undefined) {
        params.set("companyId", String(filters.companyId));
    }

    if (filters.jobPostingId !== undefined) {
        params.set("jobPostingId", String(filters.jobPostingId));
    }

    return apiRequest(`/api/interview-growth-analysis?${params.toString()}`, {
        authenticated: true,
    });
}