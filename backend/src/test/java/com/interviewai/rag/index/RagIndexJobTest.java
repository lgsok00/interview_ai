package com.interviewai.rag.index;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RagIndexJobTest {

    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

    @Test
    @DisplayName("색인 작업을 대기 상태로 생성하고 실행 후 성공 처리한다")
    void completeUpsert() {
        RagIndexTarget target = target();
        RagIndexJob job = RagIndexJob.upsert(target, 2, NOW);
        assertThat(job.getId()).isNotNull();
        assertThat(job.getSourceKey()).isEqualTo(target.snapshot().sourceKey());
        assertThat(job.getTarget()).isSameAs(target);
        assertThat(job.getOperation()).isEqualTo(RagIndexOperation.UPSERT);
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.PENDING);
        assertThat(job.getIndexStatus()).isEqualTo(RagIndexStatus.PENDING);
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getCreatedAt()).isEqualTo(NOW);
        assertThat(job.getUpdatedAt()).isEqualTo(NOW);
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getFinishedAt()).isNull();
        assertThat(job.getAttemptId()).isNull();

        UUID attempt = job.start(NOW.plusSeconds(1));
        assertThat(job.getAttemptId()).isEqualTo(attempt);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.RUNNING);
        assertThat(job.getIndexStatus()).isEqualTo(RagIndexStatus.INDEXING);
        assertThat(job.getStartedAt()).isEqualTo(NOW.plusSeconds(1));

        job.succeed(attempt, NOW.plusSeconds(2));
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.SUCCEEDED);
        assertThat(job.getIndexStatus()).isEqualTo(RagIndexStatus.INDEXED);
        assertThat(job.getFinishedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(job.getUpdatedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(job.getCreatedAt()).isEqualTo(NOW);
        assertThat(job.getFailureCode()).isNull();
    }

    @Test
    @DisplayName("삭제는 스냅샷 없이 실행하고 삭제 완료 상태로 전이한다")
    void completeDeleteAtSameInstant() {
        RagSourceKey key = new RagSourceKey(RagSourceType.RESUME, 40L);
        RagIndexJob job = RagIndexJob.delete(key, 1, NOW);
        assertThat(job.getTarget()).isNull();
        assertThat(job.getSourceKey()).isEqualTo(key);
        assertThat(job.getOperation()).isEqualTo(RagIndexOperation.DELETE);
        UUID attempt = job.start(NOW);
        assertThat(job.getIndexStatus()).isEqualTo(RagIndexStatus.DELETING);
        job.succeed(attempt, NOW);
        assertThat(job.getIndexStatus()).isEqualTo(RagIndexStatus.DELETED);
    }

    @Test
    @DisplayName("재시도는 generation을 유지하고 실행 토큰을 교체하며 이전 응답을 거부한다")
    void retryAndRejectStaleAttempt() {
        RagIndexJob job = job(2);
        UUID generation = job.getId();
        UUID first = job.start(NOW);
        job.fail(first, "VECTOR_STORE_FAILED", NOW.plusSeconds(1));
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.FAILED);
        assertThat(job.getIndexStatus()).isEqualTo(RagIndexStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo("VECTOR_STORE_FAILED");
        job.retry(NOW.plusSeconds(2));
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.PENDING);
        assertThat(job.getAttemptId()).isNull();
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getFinishedAt()).isNull();
        assertThat(job.getFailureCode()).isNull();
        assertThat(job.getAttemptCount()).isEqualTo(1);

        UUID second = job.start(NOW.plusSeconds(3));
        assertThat(second).isNotEqualTo(first);
        assertThat(job.getId()).isEqualTo(generation);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThatIllegalStateException().isThrownBy(() -> job.succeed(first, NOW.plusSeconds(4)));
        assertThatIllegalStateException().isThrownBy(() -> job.fail(first, "FAILED", NOW.plusSeconds(4)));
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.RUNNING);
        assertThat(job.getUpdatedAt()).isEqualTo(NOW.plusSeconds(3));
        job.succeed(second, NOW.plusSeconds(4));
    }

    @Test
    @DisplayName("최대 실행 횟수에 도달한 실패 작업은 재시도를 거부하고 실패 정보를 유지한다")
    void rejectExhaustedRetry() {
        RagIndexJob job = job(1);
        UUID attempt = job.start(NOW);
        job.fail(attempt, "FAILED", NOW.plusSeconds(1));
        assertThatIllegalStateException().isThrownBy(() -> job.retry(NOW.plusSeconds(2)));
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo("FAILED");
        assertThat(job.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    @DisplayName("대기 실행 실패 작업을 취소하고 취소 후 변경을 거부한다")
    void cancelAllowedStates() {
        for (RagIndexJobStatus state : new RagIndexJobStatus[]{
                RagIndexJobStatus.PENDING, RagIndexJobStatus.RUNNING, RagIndexJobStatus.FAILED}) {
            RagIndexJob job = job(2);
            UUID attempt = UUID.randomUUID();
            if (state != RagIndexJobStatus.PENDING) {
                attempt = job.start(NOW);
                if (state == RagIndexJobStatus.FAILED) {
                    job.fail(attempt, "FAILED", NOW);
                }
            }
            UUID cancelledAttempt = attempt;
            job.cancel(NOW.plusSeconds(1));
            assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.CANCELLED);
            assertThat(job.getIndexStatus()).isEqualTo(RagIndexStatus.CANCELLED);
            assertThat(job.getFinishedAt()).isEqualTo(NOW.plusSeconds(1));
            assertThatIllegalStateException().isThrownBy(() -> job.start(NOW.plusSeconds(2)));
            assertThatIllegalStateException().isThrownBy(() -> job.retry(NOW.plusSeconds(2)));
            assertThatIllegalStateException().isThrownBy(() -> job.cancel(NOW.plusSeconds(2)));
            assertThatIllegalStateException().isThrownBy(() -> job.succeed(cancelledAttempt, NOW.plusSeconds(2)));
            assertThatIllegalStateException().isThrownBy(() -> job.fail(cancelledAttempt, "FAILED", NOW.plusSeconds(2)));
        }
    }

    @Test
    @DisplayName("성공 작업은 다시 시작하거나 완료 실패 재시도 취소할 수 없다")
    void rejectChangesAfterSuccess() {
        RagIndexJob job = job(2);
        UUID attempt = job.start(NOW);
        job.succeed(attempt, NOW);
        assertThatIllegalStateException().isThrownBy(() -> job.start(NOW));
        assertThatIllegalStateException().isThrownBy(() -> job.succeed(attempt, NOW));
        assertThatIllegalStateException().isThrownBy(() -> job.fail(attempt, "FAILED", NOW));
        assertThatIllegalStateException().isThrownBy(() -> job.retry(NOW));
        assertThatIllegalStateException().isThrownBy(() -> job.cancel(NOW));
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("실행 전 완료와 재시도 및 중복 실행을 거부한다")
    void rejectInvalidTransitions() {
        RagIndexJob job = job(2);
        UUID unknown = UUID.randomUUID();
        assertThatIllegalStateException().isThrownBy(() -> job.succeed(unknown, NOW));
        assertThatIllegalStateException().isThrownBy(() -> job.fail(unknown, "FAILED", NOW));
        assertThatIllegalStateException().isThrownBy(() -> job.retry(NOW));
        job.start(NOW);
        assertThatIllegalStateException().isThrownBy(() -> job.start(NOW));
        assertThatIllegalStateException().isThrownBy(() -> job.retry(NOW));
        assertThat(job.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("잘못된 실패 코드와 실행 토큰은 실행 상태를 변경하지 않는다")
    void rejectInvalidFailureInput() {
        RagIndexJob job = job(2);
        UUID attempt = job.start(NOW);
        for (String code : new String[]{"", "failed", " FAILED", "1FAILED", "A-B", "A".repeat(101)}) {
            assertThatIllegalArgumentException().isThrownBy(() -> job.fail(attempt, code, NOW.plusSeconds(1)));
        }
        assertThatNullPointerException().isThrownBy(() -> job.fail(attempt, null, NOW));
        assertThatNullPointerException().isThrownBy(() -> job.succeed(null, NOW));
        assertThatNullPointerException().isThrownBy(() -> job.fail(null, "FAILED", NOW));
        assertThatIllegalStateException().isThrownBy(() -> job.succeed(UUID.randomUUID(), NOW));
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.RUNNING);
        assertThat(job.getUpdatedAt()).isEqualTo(NOW);
        assertThat(job.getFinishedAt()).isNull();
        assertThat(job.getFailureCode()).isNull();
    }

    @Test
    @DisplayName("실패 코드의 1자와 100자 경계를 허용한다")
    void acceptFailureCodeBoundaries() {
        for (String code : new String[]{"A", "A".repeat(100)}) {
            RagIndexJob job = job(1);
            UUID attempt = job.start(NOW);
            job.fail(attempt, code, NOW);
            assertThat(job.getFailureCode()).isEqualTo(code);
        }
    }

    @Test
    @DisplayName("각 전이의 역행 시각과 누락 시각을 거부하며 상태를 유지한다")
    void rejectInvalidTransitionTimes() {
        RagIndexJob job = job(2);
        Instant before = NOW.minusNanos(1);
        assertThatIllegalArgumentException().isThrownBy(() -> job.start(before));
        assertThatNullPointerException().isThrownBy(() -> job.start(null));
        assertThat(job.getAttemptCount()).isZero();
        UUID attempt = job.start(NOW);
        assertThatIllegalArgumentException().isThrownBy(() -> job.succeed(attempt, before));
        assertThatNullPointerException().isThrownBy(() -> job.succeed(attempt, null));
        assertThatIllegalArgumentException().isThrownBy(() -> job.fail(attempt, "FAILED", before));
        assertThatNullPointerException().isThrownBy(() -> job.fail(attempt, "FAILED", null));
        assertThatIllegalArgumentException().isThrownBy(() -> job.cancel(before));
        assertThatNullPointerException().isThrownBy(() -> job.cancel(null));
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.RUNNING);
        job.fail(attempt, "FAILED", NOW.plusSeconds(2));
        assertThatIllegalArgumentException().isThrownBy(() -> job.retry(NOW.plusSeconds(1)));
        assertThatNullPointerException().isThrownBy(() -> job.retry(null));
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.FAILED);
        assertThat(job.getUpdatedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    @DisplayName("필수 생성 값 누락과 0 이하 실행 횟수를 거부한다")
    void rejectInvalidCreation() {
        RagSourceKey key = target().snapshot().sourceKey();
        assertThatNullPointerException().isThrownBy(() -> RagIndexJob.upsert(null, 1, NOW));
        assertThatNullPointerException().isThrownBy(() -> RagIndexJob.delete(null, 1, NOW));
        assertThatNullPointerException().isThrownBy(() -> RagIndexJob.upsert(target(), 1, null));
        assertThatNullPointerException().isThrownBy(() -> RagIndexJob.delete(key, 1, null));
        for (int attempts : new int[]{0, -1}) {
            assertThatIllegalArgumentException().isThrownBy(() -> RagIndexJob.upsert(target(), attempts, NOW));
            assertThatIllegalArgumentException().isThrownBy(() -> RagIndexJob.delete(key, attempts, NOW));
        }
    }

    private RagIndexJob job(int maxAttempts) {
        return RagIndexJob.upsert(target(), maxAttempts, NOW);
    }

    private RagIndexTarget target() {
        return new RagIndexTarget(new RagSourceSnapshot(
                new RagSourceKey(RagSourceType.COMPANY, 10L),
                null, 10L, "기업", "본문", "revision-1"), "pipeline-v1");
    }
}
