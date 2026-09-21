package com.interviewai.coverletter.dto;

import com.interviewai.coverletter.entity.CoverLetterVersion;

import java.time.LocalDateTime;

public record CoverLetterVersionSummaryResponse(
        Integer versionNumber,
        String title,
        boolean current,
        LocalDateTime createdAt
) {

    public static CoverLetterVersionSummaryResponse of(CoverLetterVersion version, boolean current) {
        return new CoverLetterVersionSummaryResponse(
                version.getVersionNumber(),
                version.getTitle(),
                current,
                version.getCreatedAt()
        );
    }
}
