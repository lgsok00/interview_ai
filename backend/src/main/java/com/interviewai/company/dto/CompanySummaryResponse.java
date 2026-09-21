package com.interviewai.company.dto;

import com.interviewai.company.repository.CompanyRepository;

public record CompanySummaryResponse(
        Long id,
        String name,
        String industry,
        String location,
        boolean favorite
) {

    public static CompanySummaryResponse from(CompanyRepository.SummaryRow row) {
        return new CompanySummaryResponse(
                row.getId(),
                row.getName(),
                row.getIndustry(),
                row.getLocation(),
                row.getFavorite()
        );
    }
}
