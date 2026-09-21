package com.interviewai.coverletter.dto;

import com.interviewai.coverletter.entity.CoverLetter;

import java.time.LocalDateTime;

public record CoverLetterSummaryResponse(
        Long id,
        String title,
        Integer currentVersionNumber,
        boolean representative,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CoverLetterSummaryResponse of(CoverLetter coverLetter, boolean representative) {
        return new CoverLetterSummaryResponse(
                coverLetter.getId(),
                coverLetter.getTitle(),
                coverLetter.getCurrentVersionNumber(),
                representative,
                coverLetter.getCreatedAt(),
                coverLetter.getUpdatedAt()
        );
    }
}
