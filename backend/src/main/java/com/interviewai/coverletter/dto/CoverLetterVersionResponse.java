package com.interviewai.coverletter.dto;

import com.interviewai.coverletter.entity.CoverLetterVersion;

import java.time.LocalDateTime;

public record CoverLetterVersionResponse(
        Long coverLetterId,
        Integer versionNumber,
        String title,
        String content,
        boolean current,
        LocalDateTime createdAt
) {

    public static CoverLetterVersionResponse of(CoverLetterVersion version, boolean current) {
        return new CoverLetterVersionResponse(
                version.getCoverLetter().getId(),
                version.getVersionNumber(),
                version.getTitle(),
                version.getContent(),
                current,
                version.getCreatedAt()
        );
    }
}
