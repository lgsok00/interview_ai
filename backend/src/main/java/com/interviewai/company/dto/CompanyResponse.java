package com.interviewai.company.dto;

import com.interviewai.company.entity.Company;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record CompanyResponse(
        Long id,
        String name,
        String industry,
        String description,
        String websiteUrl,
        String location,
        boolean favorite,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static CompanyResponse of(Company company, boolean favorite) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getIndustry(),
                company.getDescription(),
                company.getWebsiteUrl(),
                company.getLocation(),
                favorite,
                company.getCreatedAt().atOffset(ZoneOffset.UTC),
                company.getUpdatedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
