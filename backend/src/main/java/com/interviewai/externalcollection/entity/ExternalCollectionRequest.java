package com.interviewai.externalcollection.entity;

import com.interviewai.externalcollection.ExternalCollectionPolicy;
import com.interviewai.externalcollection.enums.ExternalCollectionApprovedTargetType;
import com.interviewai.externalcollection.enums.ExternalCollectionKind;
import com.interviewai.externalcollection.enums.ExternalCollectionRequestStatus;
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
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@Table(name = "external_collection_requests")
public class ExternalCollectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_kind", nullable = false, length = 20)
    private ExternalCollectionKind collectionKind;

    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;

    @Column(name = "normalized_source_url", nullable = false, length = 2048)
    private String normalizedSourceUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExternalCollectionRequestStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "manual_retry_count", nullable = false)
    private Integer manualRetryCount;

    @Column(name = "attempt_id", length = 36)
    private String attemptId;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "available_at")
    private LocalDateTime availableAt;

    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    @Column(name = "approved_snapshot_id")
    private Long approvedSnapshotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approved_target_type", length = 20)
    private ExternalCollectionApprovedTargetType approvedTargetType;

    @Column(name = "approved_target_id")
    private Long approvedTargetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    protected ExternalCollectionRequest() {

    }


    private ExternalCollectionRequest(
            ExternalCollectionKind collectionKind,
            String sourceUrl,
            String normalizedSourceUrl,
            User requestedBy,
            LocalDateTime now
    ) {
        this.collectionKind = Objects.requireNonNull(collectionKind);
        this.sourceUrl = requireText(sourceUrl, "수집 URL은 필수입니다.");
        this.normalizedSourceUrl = requireText(normalizedSourceUrl, "정규화 수집 URL은 필수입니다.");
        this.requestedBy = Objects.requireNonNull(requestedBy);
        this.status = ExternalCollectionRequestStatus.PENDING;
        this.attemptCount = 0;
        this.manualRetryCount = 0;
        this.availableAt = Objects.requireNonNull(now);
        this.createdAt = now;
        this.updatedAt = now;
    }


    public static ExternalCollectionRequest create(
            ExternalCollectionKind collectionKind,
            String sourceUrl,
            String normalizedSourceUrl,
            User requestedBy,
            LocalDateTime now
    ) {
        return new ExternalCollectionRequest(collectionKind, sourceUrl, normalizedSourceUrl, requestedBy, now);
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


    public boolean canBeClaimed(LocalDateTime now) {
        Objects.requireNonNull(now);

        return (status == ExternalCollectionRequestStatus.PENDING
                && (availableAt == null || !availableAt.isAfter(now)))
                || (status == ExternalCollectionRequestStatus.RUNNING
                && leaseExpiresAt != null
                && !leaseExpiresAt.isAfter(now));
    }


    public Attempt claim(UUID nextAttemptId, LocalDateTime now) {
        Objects.requireNonNull(nextAttemptId);
        Objects.requireNonNull(now);

        if (!canBeClaimed(now)) {
            throw new IllegalStateException("수집 요청을 선점할 수 없는 상태입니다.");
        }

        int maximumAttempts = ExternalCollectionPolicy.maximumAttemptsForCurrentCycle(manualRetryCount);

        if (attemptCount >= maximumAttempts) {
            throw new IllegalStateException("현재 수집 실행 주기의 시도 횟수를 초과했습니다.");
        }

        this.attemptCount += 1;
        this.status = ExternalCollectionRequestStatus.RUNNING;
        this.attemptId = nextAttemptId.toString();
        this.leaseExpiresAt = now.plusSeconds(ExternalCollectionPolicy.LEASE_SECONDS);
        this.availableAt = null;
        this.lastErrorCode = null;
        this.updatedAt = now;

        return new Attempt(attemptCount, attemptId);
    }


    public void renewLease(String expectedAttemptId, LocalDateTime now) {
        requireRunningAttempt(expectedAttemptId, now);

        leaseExpiresAt = now.plusSeconds(ExternalCollectionPolicy.LEASE_SECONDS);
        updatedAt = now;
    }


    public void completeReviewReady(String expectedAttemptId, LocalDateTime now) {
        requireRunningAttempt(expectedAttemptId, now);

        status = ExternalCollectionRequestStatus.REVIEW_READY;
        clearExecution();

        lastErrorCode = null;
        updatedAt = now;
    }


    public void fail(String expectedAttemptId, String failureCode, LocalDateTime now) {
        requireRunningAttempt(expectedAttemptId, now);
        String validatedFailureCode = requireFailureCode(failureCode);

        status = ExternalCollectionRequestStatus.FAILED;
        clearExecution();

        lastErrorCode = validatedFailureCode;
        updatedAt = now;
    }


    public void retry(LocalDateTime now) {
        if (status != ExternalCollectionRequestStatus.FAILED) {
            throw new IllegalStateException("실패한 수집 요청만 재시도할 수 있습니다.");
        }

        if (manualRetryCount >= ExternalCollectionPolicy.MAX_MANUAL_RETRIES) {
            throw new IllegalStateException("수동 재시도 한도를 초과했습니다.");
        }

        manualRetryCount += 1;
        status = ExternalCollectionRequestStatus.PENDING;
        availableAt = now;
        lastErrorCode = null;
        updatedAt = now;
    }


    public void reject(LocalDateTime now) {
        if (status != ExternalCollectionRequestStatus.REVIEW_READY) {
            throw new IllegalStateException("검수 대기 수집 요청만 폐기할 수 있습니다.");
        }

        status = ExternalCollectionRequestStatus.REJECTED;
        updatedAt = now;
    }


    private void requireRunningAttempt(String expectedAttemptId, LocalDateTime now) {
        boolean invalidAttempt = status != ExternalCollectionRequestStatus.RUNNING
                || !Objects.equals(attemptId, expectedAttemptId)
                || leaseExpiresAt == null
                || !leaseExpiresAt.isAfter(now);

        if (invalidAttempt) {
            throw new IllegalStateException("유효하지 않거나 만료된 수집 실행입니다.");
        }
    }


    private void clearExecution() {
        attemptId = null;
        leaseExpiresAt = null;
        availableAt = null;
    }


    public record Attempt(int number, String id) {

    }
}
