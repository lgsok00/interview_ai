package com.interviewai.resume.entity;

import com.interviewai.resume.enums.ResumeExtractionStatus;
import com.interviewai.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "resumes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_resumes_storage_key",
                        columnNames = "storage_key"
                ),
                @UniqueConstraint(
                        name = "uk_resumes_user_id_id",
                        columnNames = {"user_id", "id"}
                )
        }
)
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "original_filename", nullable = false)
    private String originalFileName;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "extracted_text", columnDefinition = "MEDIUMTEXT")
    private String extractedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 20)
    private ResumeExtractionStatus extractionStatus;

    @Column(name = "extraction_failure_code", length = 50)
    private String extractionFailureCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    protected Resume() {

    }


    private Resume(User user,
                   String title,
                   String originalFileName,
                   String storageKey,
                   String contentType,
                   long fileSize,
                   String sha256
    ) {
        this.user = user;
        this.title = title;
        replaceFileMetadata(originalFileName, storageKey, contentType, fileSize, sha256);
    }


    public static Resume create(
            User user,
            String title,
            String originalFileName,
            String storageKey,
            String contentType,
            long fileSize,
            String sha256
    ) {
        return new Resume(user, title, originalFileName, storageKey, contentType, fileSize, sha256);
    }


    public void updateTitle(String title) {
        this.title = title;
    }


    public void replaceFile(String originalFileName, String storageKey, String contentType, long fileSize, String sha256) {
        replaceFileMetadata(originalFileName, storageKey, contentType, fileSize, sha256);
    }


    public void completeExtraction(String extractedText) {
        this.extractedText = extractedText;
        this.extractionStatus = ResumeExtractionStatus.COMPLETED;
        this.extractionFailureCode = null;
    }


    public void failExtraction(String failureCode) {
        this.extractedText = null;
        this.extractionStatus = ResumeExtractionStatus.FAILED;
        this.extractionFailureCode = failureCode;
    }


    private void replaceFileMetadata(
            String originalFileName,
            String storageKey,
            String contentType,
            long fileSize,
            String sha256
    ) {
        this.originalFileName = originalFileName;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.extractedText = null;
        this.extractionStatus = ResumeExtractionStatus.PENDING;
        this.extractionFailureCode = null;
    }


    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;
    }


    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
