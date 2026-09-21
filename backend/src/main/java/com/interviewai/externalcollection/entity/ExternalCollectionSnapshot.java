package com.interviewai.externalcollection.entity;

import com.interviewai.externalcollection.enums.ExternalCollectionSnapshotStatus;
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
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Table(name = "external_collection_snapshots")
public class ExternalCollectionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ExternalCollectionRequest request;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "attempt_id", nullable = false, length = 36)
    private String attemptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExternalCollectionSnapshotStatus status;

    @Column(name = "final_url", length = 2048)
    private String finalUrl;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "body_sha256", length = 64)
    private String bodySha256;

    @Column(name = "extracted_text", columnDefinition = "MEDIUMTEXT")
    private String extractedText;

    @Column(name = "candidate_json", columnDefinition = "JSON")
    private String candidateJson;

    @Column(name = "evidence_json", columnDefinition = "JSON")
    private String evidenceJson;

    @Column(name = "parser_version", length = 30)
    private String parserVersion;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;


    protected ExternalCollectionSnapshot() {

    }


    private ExternalCollectionSnapshot(
            ExternalCollectionRequest request,
            int attemptNumber,
            String attemptId,
            ExternalCollectionSnapshotStatus status,
            String finalUrl,
            Integer httpStatus,
            String contentType,
            String bodySha256,
            String extractedText,
            String candidateJson,
            String evidenceJson,
            String parserVersion,
            String failureCode,
            LocalDateTime collectedAt
    ) {
        this.request = Objects.requireNonNull(request);
        this.attemptNumber = attemptNumber;
        this.attemptId = Objects.requireNonNull(attemptId);
        this.status = Objects.requireNonNull(status);
        this.finalUrl = finalUrl;
        this.httpStatus = httpStatus;
        this.contentType = contentType;
        this.bodySha256 = bodySha256;
        this.extractedText = extractedText;
        this.candidateJson = candidateJson;
        this.evidenceJson = evidenceJson;
        this.parserVersion = parserVersion;
        this.failureCode = failureCode;
        this.collectedAt = Objects.requireNonNull(collectedAt);
    }


    public static ExternalCollectionSnapshot success(
            ExternalCollectionRequest request,
            int attemptNumber,
            String attemptId,
            String finalUrl,
            int httpStatus,
            String contentType,
            String bodySha256,
            String extractedText,
            String candidateJson,
            String evidenceJson,
            String parserVersion,
            LocalDateTime collectedAt
    ) {
        return new ExternalCollectionSnapshot(
                request,
                attemptNumber,
                attemptId,
                ExternalCollectionSnapshotStatus.SUCCEEDED,
                requireText(finalUrl, "최종 URL은 필수입니다."),
                httpStatus,
                requireText(contentType, "응답 Content-Type은 필수입니다."),
                requireText(bodySha256, "본문 SHA-256은 필수입니다."),
                requireText(extractedText, "추출 텍스트는 필수입니다."),
                candidateJson,
                evidenceJson,
                requireText(parserVersion, "파서 버전은 필수입니다."),
                null,
                collectedAt
        );
    }


    public static ExternalCollectionSnapshot failure(
            ExternalCollectionRequest request,
            int attemptNumber,
            String attemptId,
            String failureCode,
            LocalDateTime collectedAt
    ) {
        return new ExternalCollectionSnapshot(
                request,
                attemptNumber,
                attemptId,
                ExternalCollectionSnapshotStatus.FAILED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                requireFailureCode(failureCode),
                collectedAt
        );
    }


    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }


    private static String requireFailureCode(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,49}")) {
            throw new IllegalArgumentException("수집 실패 코드 형식이 올바르지 않습니다.");
        }

        return value;
    }
}