package com.interviewai.interview.dto;

import com.interviewai.interview.entity.InterviewAnswerEvaluation;
import com.interviewai.interview.enums.AnswerEvaluationStatus;

import java.time.LocalDateTime;

public record AnswerEvaluationResponse(
        Long id,
        Long answerId,
        Long questionId,
        AnswerEvaluationStatus status,
        Integer starScore,
        Integer logicScore,
        Integer jobFitScore,
        String strengths,
        String improvements,
        String improvedAnswer,
        int attemptCount,
        int manualRetryCount,
        String failureCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {

    public static AnswerEvaluationResponse from(InterviewAnswerEvaluation evaluation) {
        return new AnswerEvaluationResponse(
                evaluation.getId(),
                evaluation.getAnswer().getId(),
                evaluation.getAnswer().getQuestion().getId(),
                evaluation.getStatus(),
                evaluation.getStarScore(),
                evaluation.getLogicScore(),
                evaluation.getJobFitScore(),
                evaluation.getStrengths(),
                evaluation.getImprovements(),
                evaluation.getImprovedAnswer(),
                evaluation.getAttemptCount(),
                evaluation.getManualRetryCount(),
                evaluation.getFailureCode(),
                evaluation.getCreatedAt(),
                evaluation.getUpdatedAt(),
                evaluation.getCompletedAt()
        );
    }
}
