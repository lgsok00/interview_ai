package com.interviewai.rag.index;

import com.interviewai.rag.document.RagSourceKey;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public final class RagIndexJob {

    private final UUID id;
    private final RagSourceKey sourceKey;
    private final RagIndexOperation operation;
    private final RagIndexTarget target;
    private final int maxAttempts;
    private final Instant createdAt;

    private RagIndexJobStatus status;
    private int attemptCount;
    private UUID attemptId;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant updatedAt;
    private String failureCode;


    private RagIndexJob(
            RagSourceKey sourceKey,
            RagIndexOperation operation,
            RagIndexTarget target,
            int maxAttempts,
            Instant now
    ) {
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey는 필수입니다.");
        this.operation = Objects.requireNonNull(operation, "operation은 필수입니다.");

        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts는 1 이상이어야 합니다.");
        }

        this.id = UUID.randomUUID();
        this.target = target;
        this.maxAttempts = maxAttempts;
        this.createdAt = Objects.requireNonNull(now, "now는 필수입니다.");
        this.updatedAt = now;
        this.status = RagIndexJobStatus.PENDING;
    }


    public static RagIndexJob upsert(RagIndexTarget target, int maxAttempts, Instant now) {
        Objects.requireNonNull(target, "target은 필수입니다.");

        return new RagIndexJob(
                target.snapshot().sourceKey(),
                RagIndexOperation.UPSERT,
                target,
                maxAttempts,
                now
        );
    }


    public static RagIndexJob delete(RagSourceKey sourceKey, int maxAttempts, Instant now) {
        return new RagIndexJob(
                sourceKey,
                RagIndexOperation.DELETE,
                null,
                maxAttempts,
                now
        );
    }


    private static String validateFailureCode(String value) {
        Objects.requireNonNull(value, "failureCode는 필수입니다.");

        if (!value.matches("[A-Z][A-Z0-9_]{0,99}")) {
            throw new IllegalArgumentException("failureCode는 100자 이하의 영문 대문자·숫자·밑줄 코드여야 합니다.");
        }

        return value;
    }


    public UUID start(Instant now) {
        requireStatus(RagIndexJobStatus.PENDING);
        validateTime(now);

        if (attemptCount >= maxAttempts) {
            throw new IllegalStateException("최대 실행 횟수를 초과했습니다.");
        }

        UUID nextAttemptId = UUID.randomUUID();

        status = RagIndexJobStatus.RUNNING;
        attemptCount++;
        attemptId = nextAttemptId;
        startedAt = now;
        finishedAt = null;
        failureCode = null;
        updatedAt = now;

        return nextAttemptId;
    }


    public void succeed(UUID expectedAttemptId, Instant now) {
        requireRunningAttempt(expectedAttemptId);
        validateTime(now);

        status = RagIndexJobStatus.SUCCEEDED;
        finishedAt = now;
        updatedAt = now;
    }


    public void fail(UUID expectedAttemptId, String failureCode, Instant now) {
        requireRunningAttempt(expectedAttemptId);
        validateTime(now);
        String validatedCode = validateFailureCode(failureCode);

        status = RagIndexJobStatus.FAILED;
        this.failureCode = validatedCode;
        finishedAt = now;
        updatedAt = now;
    }


    public void retry(Instant now) {
        requireStatus(RagIndexJobStatus.FAILED);
        validateTime(now);

        if (attemptCount >= maxAttempts) {
            throw new IllegalStateException("재시도 가능한 횟수가 없습니다.");
        }

        status = RagIndexJobStatus.PENDING;
        attemptId = null;
        startedAt = null;
        finishedAt = null;
        failureCode = null;
        updatedAt = now;
    }


    public void cancel(Instant now) {
        if (status != RagIndexJobStatus.PENDING
                && status != RagIndexJobStatus.RUNNING
                && status != RagIndexJobStatus.FAILED
        ) {
            throw new IllegalStateException("현재 상태에서는 작업을 취소할 수 없습니다.");
        }

        validateTime(now);

        status = RagIndexJobStatus.CANCELLED;
        finishedAt = now;
        updatedAt = now;
    }


    public RagIndexStatus getIndexStatus() {
        return switch (status) {
            case PENDING -> RagIndexStatus.PENDING;
            case RUNNING -> operation == RagIndexOperation.UPSERT ? RagIndexStatus.INDEXING : RagIndexStatus.DELETING;
            case SUCCEEDED -> operation == RagIndexOperation.UPSERT ? RagIndexStatus.INDEXED : RagIndexStatus.DELETED;
            case FAILED -> RagIndexStatus.FAILED;
            case CANCELLED -> RagIndexStatus.CANCELLED;
        };
    }


    public void requireRunningAttempt(UUID expectedAttemptId) {
        requireStatus(RagIndexJobStatus.RUNNING);
        Objects.requireNonNull(expectedAttemptId, "expectedAttemptId는 필수입니다.");

        if (!expectedAttemptId.equals(attemptId)) {
            throw new IllegalStateException("현재 실행 시도와 일치하지 않습니다.");
        }
    }


    private void requireStatus(RagIndexJobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("작업 상태가 " + expected + "이어야 합니다.");
        }
    }


    private void validateTime(Instant now) {
        Objects.requireNonNull(now, "now는 필수입니다.");

        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("작업 시각은 이전 변경 시각보다 빠를 수 없습니다.");
        }
    }
}
