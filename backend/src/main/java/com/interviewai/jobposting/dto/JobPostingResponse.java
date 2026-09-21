package com.interviewai.jobposting.dto;

import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.enums.EmploymentType;
import com.interviewai.jobposting.enums.JobPostingStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record JobPostingResponse(
        Long id,
        Long companyId,
        String companyName,
        String title,
        String jobRole,
        EmploymentType employmentType,
        String location,
        String description,
        String sourceUrl,
        OffsetDateTime opensAt,
        OffsetDateTime closesAt,
        boolean manuallyClosed,
        JobPostingStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static JobPostingResponse of(JobPosting jobPosting, LocalDateTime now) {
        return new JobPostingResponse(
                jobPosting.getId(),
                jobPosting.getCompany().getId(),
                jobPosting.getCompany().getName(),
                jobPosting.getTitle(),
                jobPosting.getJobRole(),
                jobPosting.getEmploymentType(),
                jobPosting.getLocation(),
                jobPosting.getDescription(),
                jobPosting.getSourceUrl(),
                toOffsetDateTime(jobPosting.getOpensAt()),
                toOffsetDateTime(jobPosting.getClosesAt()),
                jobPosting.isManuallyClosed(),
                jobPosting.statusAt(now),
                toOffsetDateTime(jobPosting.getCreatedAt()),
                toOffsetDateTime(jobPosting.getUpdatedAt())
        );
    }


    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
