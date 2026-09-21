package com.interviewai.coverletter.dto;

import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;

import java.time.LocalDateTime;

public record CoverLetterResponse(
        Long id,
        String title,
        String content,
        Integer currentVersionNumber,
        boolean representative,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CoverLetterResponse of(CoverLetter coverLetter, CoverLetterVersion version, boolean representative) {
        return new CoverLetterResponse(
                coverLetter.getId(),
                coverLetter.getTitle(),
                version.getContent(),
                coverLetter.getCurrentVersionNumber(),
                representative,
                coverLetter.getCreatedAt(),
                coverLetter.getUpdatedAt()
        );
    }
}
