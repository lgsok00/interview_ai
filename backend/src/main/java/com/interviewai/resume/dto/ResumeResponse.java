package com.interviewai.resume.dto;

import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.enums.ResumeExtractionStatus;

import java.time.LocalDateTime;

public record ResumeResponse(
        Long id,
        String title,
        String originalFilename,
        String contentType,
        long fileSize,
        String sha256,
        String extractedText,
        ResumeExtractionStatus extractionStatus,
        String extractionFailureCode,
        boolean representative,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ResumeResponse of(Resume resume, boolean representative) {
        return new ResumeResponse(
                resume.getId(),
                resume.getTitle(),
                resume.getOriginalFileName(),
                resume.getContentType(),
                resume.getFileSize(),
                resume.getSha256(),
                resume.getExtractedText(),
                resume.getExtractionStatus(),
                resume.getExtractionFailureCode(),
                representative,
                resume.getCreatedAt(),
                resume.getUpdatedAt()
        );
    }
}
