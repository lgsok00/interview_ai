package com.interviewai.jobposting.dto;

import com.interviewai.jobposting.enums.EmploymentType;
import com.interviewai.jobposting.enums.JobPostingStatus;
import com.interviewai.jobposting.repository.JobPostingRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record JobPostingSummaryResponse(
        Long id,
        Long companyId,
        String companyName,
        String title,
        String jobRole,
        EmploymentType employmentType,
        String location,
        JobPostingStatus status,
        OffsetDateTime opensAt,
        OffsetDateTime closesAt
) {

    public static JobPostingSummaryResponse from(JobPostingRepository.SummaryRow row) {
        return new JobPostingSummaryResponse(
                row.getId(),
                row.getCompanyId(),
                row.getCompanyName(),
                row.getTitle(),
                row.getJobRole(),
                row.getEmploymentType(),
                row.getLocation(),
                JobPostingStatus.valueOf(row.getStatus()),
                row.getOpensAt() == null ? null : row.getOpensAt().atOffset(ZoneOffset.UTC),
                row.getClosesAt() == null ? null : row.getClosesAt().atOffset(ZoneOffset.UTC)
        );
    }
}
