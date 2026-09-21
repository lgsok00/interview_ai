package com.interviewai.resume.dto;

import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.enums.ResumeExtractionStatus;

import java.time.LocalDateTime;

public record ResumeSummaryResponse(
        Long id,
        String title,
        String originalFilename,
        long fileSize,
        ResumeExtractionStatus extractionStatus,
        boolean representative,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ResumeSummaryResponse of(Resume resume, boolean representative) {
        return new ResumeSummaryResponse(
                resume.getId(),
                resume.getTitle(),
                resume.getOriginalFileName(),
                resume.getFileSize(),
                resume.getExtractionStatus(),
                representative,
                resume.getCreatedAt(),
                resume.getUpdatedAt()
        );
    }
}
