package com.interviewai.company.dto;

import com.interviewai.company.repository.CompanyRepository;
import org.springframework.data.domain.Page;

import java.util.List;

public record CompanyPageResponse(
        List<CompanySummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static CompanyPageResponse from(Page<CompanyRepository.SummaryRow> result) {
        List<CompanySummaryResponse> items = result.getContent()
                .stream()
                .map(CompanySummaryResponse::from)
                .toList();

        return new CompanyPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
