package com.interviewai.rag.metadata;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.index.RagIndexJob;
import com.interviewai.rag.index.RagIndexTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RagChunkMetadataTest {

    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

    @Test
    @DisplayName("기업 metadata는 숫자 ID와 공용 범위를 포함하고 개인 필드를 생략한다")
    void createCompanyMetadata() {
        RagIndexJob job = job(RagSourceType.COMPANY);
        UUID attempt = job.start(NOW);
        Map<String, Object> metadata = RagChunkMetadata.from(job, attempt, 0, 1).getMetadata();
        assertThat(metadata).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("schemaVersion", 1),
                Map.entry("sourceType", "COMPANY"),
                Map.entry("sourceId", 10L),
                Map.entry("visibility", "AUTHENTICATED_SHARED"),
                Map.entry("sourceRevision", "revision-1"),
                Map.entry("pipelineVersion", "pipeline-v1"),
                Map.entry("generationId", job.getId().toString()),
                Map.entry("chunkIndex", 0),
                Map.entry("chunkCount", 1),
                Map.entry("title", "제목"),
                Map.entry("companyId", 10L)
        ));
    }

    @Test
    @DisplayName("채용공고 metadata는 공고 ID와 소속 기업 ID를 구분한다")
    void createJobPostingMetadata() {
        RagIndexJob job = job(RagSourceType.JOB_POSTING);
        Map<String, Object> metadata = RagChunkMetadata.from(job, job.start(NOW), 0, 1).getMetadata();
        assertThat(metadata).containsEntry("sourceType", "JOB_POSTING")
                .containsEntry("sourceId", 20L)
                .containsEntry("jobPostingId", 20L)
                .containsEntry("companyId", 10L)
                .containsEntry("visibility", "AUTHENTICATED_SHARED")
                .doesNotContainKeys("ownerUserId", "content");
    }

    @Test
    @DisplayName("개인 문서는 소유자만 포함하고 기업과 공고 ID를 생략한다")
    void createPrivateMetadata() {
        for (RagSourceType type : new RagSourceType[]{RagSourceType.COVER_LETTER, RagSourceType.RESUME}) {
            RagIndexJob job = job(type);
            Map<String, Object> metadata = RagChunkMetadata.from(job, job.start(NOW), 0, 1).getMetadata();
            assertThat(metadata).containsEntry("sourceType", type.name())
                    .containsEntry("sourceId", 20L)
                    .containsEntry("ownerUserId", 7L)
                    .containsEntry("visibility", "PRIVATE")
                    .doesNotContainKeys("companyId", "jobPostingId", "content");
        }
    }

    @Test
    @DisplayName("metadata를 외부에서 변경할 수 없고 작업 전이에도 생성된 값이 유지된다")
    // 불변 Map의 변경 시도가 예외로 거부되는지 의도적으로 검증한다.
    @SuppressWarnings("DataFlowIssue")
    void preserveImmutableMetadata() {
        RagIndexJob job = job(RagSourceType.RESUME);
        UUID attempt = job.start(NOW);
        RagChunkMetadata chunk = RagChunkMetadata.from(job, attempt, 0, 1);
        Map<String, Object> expected = Map.copyOf(chunk.getMetadata());
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> chunk.getMetadata().put("ownerUserId", 999L));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> chunk.getMetadata().remove("visibility"));
        job.succeed(attempt, NOW);
        assertThat(chunk.getMetadata()).isEqualTo(expected);
    }

    @Test
    @DisplayName("동일 chunk는 같은 ID를 사용하고 다른 chunk와 새 작업은 다른 ID를 사용한다")
    void distinguishChunksAndGenerations() {
        RagIndexJob firstJob = job(RagSourceType.COMPANY);
        UUID firstAttempt = firstJob.start(NOW);
        RagChunkMetadata first = RagChunkMetadata.from(firstJob, firstAttempt, 0, 2);
        RagChunkMetadata repeated = RagChunkMetadata.from(firstJob, firstAttempt, 0, 2);
        RagChunkMetadata last = RagChunkMetadata.from(firstJob, firstAttempt, 1, 2);
        RagIndexJob secondJob = job(RagSourceType.COMPANY);
        RagChunkMetadata nextGeneration = RagChunkMetadata.from(secondJob, secondJob.start(NOW), 0, 2);
        assertThat(first.getPointId()).isNotNull().isEqualTo(repeated.getPointId());
        assertThat(last.getPointId()).isNotEqualTo(first.getPointId());
        assertThat(last.getMetadata()).containsEntry("chunkIndex", 1).containsEntry("chunkCount", 2);
        assertThat(nextGeneration.getPointId()).isNotEqualTo(first.getPointId());
        assertThat(nextGeneration.getMetadata().get("generationId"))
                .isNotEqualTo(first.getMetadata().get("generationId"));
    }

    @Test
    @DisplayName("재시도는 point ID와 metadata를 유지하며 이전 실행 토큰을 거부한다")
    void preservePointOnRetry() {
        RagIndexJob job = job(RagSourceType.COMPANY);
        UUID firstAttempt = job.start(NOW);
        RagChunkMetadata first = RagChunkMetadata.from(job, firstAttempt, 0, 1);
        job.fail(firstAttempt, "VECTOR_STORE_FAILED", NOW);
        job.retry(NOW);
        UUID secondAttempt = job.start(NOW);
        assertThatIllegalStateException()
                .isThrownBy(() -> RagChunkMetadata.from(job, firstAttempt, 0, 1));
        RagChunkMetadata retried = RagChunkMetadata.from(job, secondAttempt, 0, 1);
        assertThat(retried.getPointId()).isEqualTo(first.getPointId());
        assertThat(retried.getMetadata()).isEqualTo(first.getMetadata());
    }

    @Test
    @DisplayName("음수 chunk 번호는 양수 chunk 개수에서도 거부한다")
    void rejectNegativeChunkIndex() {
        RagIndexJob job = job(RagSourceType.COMPANY);
        UUID attempt = job.start(NOW);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RagChunkMetadata.from(job, attempt, -1, 1));
    }

    @Test
    @DisplayName("음수 chunk 번호와 0개 chunk 조합을 거부한다")
    void rejectZeroCountWithNegativeIndex() {
        RagIndexJob job = job(RagSourceType.COMPANY);
        UUID attempt = job.start(NOW);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RagChunkMetadata.from(job, attempt, -1, 0));
    }

    @Test
    @DisplayName("0 이하 chunk 개수와 개수 이상의 chunk 번호를 거부한다")
    void rejectOtherInvalidChunkBounds() {
        RagIndexJob job = job(RagSourceType.COMPANY);
        UUID attempt = job.start(NOW);
        for (int[] pair : new int[][]{{0, 0}, {0, -1}, {1, 1}, {2, 1}}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> RagChunkMetadata.from(job, attempt, pair[0], pair[1]));
        }
    }

    @Test
    @DisplayName("삭제 작업과 누락된 작업 또는 실행 토큰을 거부한다")
    void rejectDeleteAndMissingInputs() {
        RagIndexJob deletion = RagIndexJob.delete(new RagSourceKey(RagSourceType.RESUME, 20L), 1, NOW);
        UUID deletionAttempt = deletion.start(NOW);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RagChunkMetadata.from(deletion, deletionAttempt, 0, 1));
        assertThatNullPointerException()
                .isThrownBy(() -> RagChunkMetadata.from(null, UUID.randomUUID(), 0, 1));
        RagIndexJob job = job(RagSourceType.COMPANY);
        job.start(NOW);
        assertThatNullPointerException().isThrownBy(() -> RagChunkMetadata.from(job, null, 0, 1));
        assertThatIllegalStateException()
                .isThrownBy(() -> RagChunkMetadata.from(job, UUID.randomUUID(), 0, 1));
    }

    @Test
    @DisplayName("대기 실패 성공 취소 상태에서는 metadata 생성을 거부한다")
    void rejectNonRunningJobs() {
        RagIndexJob pending = job(RagSourceType.COMPANY);
        assertThatIllegalStateException()
                .isThrownBy(() -> RagChunkMetadata.from(pending, UUID.randomUUID(), 0, 1));
        RagIndexJob failed = job(RagSourceType.COMPANY);
        UUID failedAttempt = failed.start(NOW);
        failed.fail(failedAttempt, "FAILED", NOW);
        assertThatIllegalStateException()
                .isThrownBy(() -> RagChunkMetadata.from(failed, failedAttempt, 0, 1));
        RagIndexJob succeeded = job(RagSourceType.COMPANY);
        UUID succeededAttempt = succeeded.start(NOW);
        succeeded.succeed(succeededAttempt, NOW);
        assertThatIllegalStateException()
                .isThrownBy(() -> RagChunkMetadata.from(succeeded, succeededAttempt, 0, 1));
        RagIndexJob cancelled = job(RagSourceType.COMPANY);
        UUID cancelledAttempt = cancelled.start(NOW);
        cancelled.cancel(NOW);
        assertThatIllegalStateException()
                .isThrownBy(() -> RagChunkMetadata.from(cancelled, cancelledAttempt, 0, 1));
    }

    private RagIndexJob job(RagSourceType type) {
        long sourceId = type == RagSourceType.COMPANY ? 10L : 20L;
        Long ownerId = type.isPrivate() ? 7L : null;
        Long companyId = type.belongsToCompany() ? 10L : null;
        RagSourceSnapshot snapshot = new RagSourceSnapshot(new RagSourceKey(type, sourceId),
                ownerId, companyId, "제목", "본문", "revision-1");
        return RagIndexJob.upsert(new RagIndexTarget(snapshot, "pipeline-v1"), 2, NOW);
    }
}
