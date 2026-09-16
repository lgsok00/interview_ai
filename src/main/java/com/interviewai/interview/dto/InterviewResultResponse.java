package com.interviewai.interview.dto;

import com.interviewai.interview.enums.AnswerEvaluationStatus;
import com.interviewai.interview.enums.InterviewAnalysisStatus;
import com.interviewai.interview.enums.InterviewQuestionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record InterviewResultResponse(
        Session session,
        InterviewAnalysisStatus analysisStatus,
        int answerCount,
        int completedEvaluationCount,
        int pendingEvaluationCount,
        int processingEvaluationCount,
        int failedEvaluationCount,
        int notRequestedEvaluationCount,
        ScoreSummary overall,
        List<QuestionTypeSummary> byQuestionType,
        List<QuestionResult> questions
) {

    public InterviewResultResponse {
        byQuestionType = List.copyOf(byQuestionType);
        questions = List.copyOf(questions);
    }


    public record Session(
            Long id,
            String companyName,
            String jobPostingTitle,
            String jobRole,
            OffsetDateTime completedAt
    ) {

    }


    public record ScoreSummary(
            int sampleCount,
            BigDecimal averageScore,
            BigDecimal starScore,
            BigDecimal logicScore,
            BigDecimal jobFitScore
    ) {

    }


    public record QuestionTypeSummary(
            InterviewQuestionType questionType,
            int answerCount,
            int evaluatedCount,
            BigDecimal averageScore,
            BigDecimal starScore,
            BigDecimal logicScore,
            BigDecimal jobFitScore
    ) {

    }


    public record QuestionResult(
            Long questionId,
            Long parentQuestionId,
            InterviewQuestionType questionType,
            int sequence,
            String question,
            Long answerId,
            String answer,
            Evaluation evaluation
    ) {

    }


    public record Evaluation(
            AnswerEvaluationStatus status,
            Integer starScore,
            Integer logicScore,
            Integer jobFitScore,
            BigDecimal averageScore,
            String strengths,
            String improvements,
            String improvedAnswer,
            String failureCode,
            OffsetDateTime completedAt
    ) {

    }
}
