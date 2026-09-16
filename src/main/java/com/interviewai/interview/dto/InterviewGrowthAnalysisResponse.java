package com.interviewai.interview.dto;

import com.interviewai.interview.enums.InterviewQuestionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record InterviewGrowthAnalysisResponse(
        Period period,
        Filters filters,
        Summary summary,
        List<QuestionTypeSummary> byQuestionType,
        List<Trend> trends,
        Change change,
        List<Insight> strengths,
        List<Insight> weaknesses,
        List<LearningRoadmap> learningRoadmap,
        boolean insufficientData,
        int minimumEvaluatedAnswerCount
) {

    public InterviewGrowthAnalysisResponse {
        byQuestionType = List.copyOf(byQuestionType);
        trends = List.copyOf(trends);
        strengths = List.copyOf(strengths);
        weaknesses = List.copyOf(weaknesses);
        learningRoadmap = List.copyOf(learningRoadmap);
    }


    public record Period(LocalDate from, LocalDate to) {

    }


    public record Filters(String jobRole, Long companyId, Long jobPostingId) {

    }


    public record Summary(
            int sessionCount,
            int evaluatedAnswerCount,
            int excludedSessionCount,
            BigDecimal averageScore,
            BigDecimal starScore,
            BigDecimal logicScore,
            BigDecimal jobFitScore
    ) {

    }


    public record QuestionTypeSummary(
            InterviewQuestionType questionType,
            int evaluatedCount,
            BigDecimal averageScore,
            BigDecimal starScore,
            BigDecimal logicScore,
            BigDecimal jobFitScore
    ) {

    }


    public record Trend(
            Long sessionId,
            OffsetDateTime completedAt,
            String companyName,
            String jobRole,
            int evaluatedAnswerCount,
            BigDecimal averageScore,
            BigDecimal starScore,
            BigDecimal logicScore,
            BigDecimal jobFitScore
    ) {

    }


    public record Change(
            String basis,
            int recentSessionCount,
            int previousSessionCount,
            BigDecimal averageScore,
            BigDecimal starScore,
            BigDecimal logicScore,
            BigDecimal jobFitScore
    ) {

    }


    public record Insight(
            String dimension,
            InterviewQuestionType questionType,
            BigDecimal score,
            int sampleCount,
            String reason
    ) {

    }


    public record LearningRoadmap(
            int priority,
            String dimension,
            InterviewQuestionType questionType,
            BigDecimal currentScore,
            BigDecimal targetScore,
            String title,
            List<String> actions,
            Evidence evidence
    ) {

        public LearningRoadmap {
            actions = List.copyOf(actions);
        }
    }


    public record Evidence(int sampleCount, List<Long> relatedSessionIds) {

        public Evidence {
            relatedSessionIds = List.copyOf(relatedSessionIds);
        }
    }
}
