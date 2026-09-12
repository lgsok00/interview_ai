package com.interviewai.interview.dto;

import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.enums.InterviewSessionStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record InterviewSessionResponse(
        Long id,
        Long jobPostingId,
        Long coverLetterId,
        Long resumeId,
        InterviewSessionStatus status,
        String companyName,
        String jobPostingTitle,
        String jobRole,
        String jobPostingContent,
        String coverLetterTitle,
        String coverLetterContent,
        String resumeTitle,
        String resumeContent,
        String failureCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static InterviewSessionResponse from(InterviewSession session) {
        return new InterviewSessionResponse(
                session.getId(),
                session.getJobPostingId(),
                session.getCoverLetterId(),
                session.getResumeId(),
                session.getStatus(),
                session.getCompanyName(),
                session.getJobPostingTitle(),
                session.getJobRole(),
                session.getJobPostingContent(),
                session.getCoverLetterTitle(),
                session.getCoverLetterContent(),
                session.getResumeTitle(),
                session.getResumeContent(),
                session.getFailureCode(),
                session.getCreatedAt().atOffset(ZoneOffset.UTC),
                session.getUpdatedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
