package com.interviewai.jobposting.dto;

import com.interviewai.jobposting.repository.JobPostingRepository;
import org.springframework.data.domain.Page;

import java.util.List;

public record JobPostingPageResponse(
        List<JobPostingSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static JobPostingPageResponse from(Page<JobPostingRepository.SummaryRow> result) {
        List<JobPostingSummaryResponse> items = result.getContent()
                .stream()
                .map(JobPostingSummaryResponse::from)
                .toList();

        return new JobPostingPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
