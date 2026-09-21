package com.interviewai.interview.dto;

import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.enums.InterviewSessionStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record InterviewSessionSummaryResponse(
        Long id,
        Long jobPostingId,
        InterviewSessionStatus status,
        String companyName,
        String jobPostingTitle,
        String jobRole,
        String failureCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {

    public static InterviewSessionSummaryResponse from(InterviewSession session) {
        return new InterviewSessionSummaryResponse(
                session.getId(),
                session.getJobPostingId(),
                session.getStatus(),
                session.getCompanyName(),
                session.getJobPostingTitle(),
                session.getJobRole(),
                session.getFailureCode(),
                session.getCreatedAt().atOffset(ZoneOffset.UTC),
                session.getUpdatedAt().atOffset(ZoneOffset.UTC),
                session.getCompletedAt() == null ? null : session.getCompletedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
