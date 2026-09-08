package com.interviewai.rag.service;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.service.RagIndexJobExecutionService.ClaimedJob;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RagClaimedJobGenerationTest {

    @Test
    void pointsAreStableWithinAttemptButIsolatedAcrossChunksJobsAndAttempts() {
        UUID attempt = UUID.randomUUID();
        ClaimedJob first = job(1, attempt, RagIndexOperation.UPSERT);
        assertThat(first.generationId()).isEqualTo(attempt);
        assertThat(first.pointId(0)).isEqualTo(job(1, attempt, RagIndexOperation.UPSERT).pointId(0));
        assertThat(first.pointId(0)).isNotEqualTo(first.pointId(1));
        assertThat(first.pointId(0)).isNotEqualTo(job(2, attempt, RagIndexOperation.UPSERT).pointId(0));
        ClaimedJob retry = job(1, UUID.randomUUID(), RagIndexOperation.UPSERT);
        assertThat(retry.generationId()).isNotEqualTo(first.generationId());
        assertThat(retry.pointId(0)).isNotEqualTo(first.pointId(0));
        assertThat(first.pointId(Integer.MAX_VALUE)).isNotNull();
    }

    @Test
    void rejectsNegativeChunkAndDeletePointCreation() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> job(1, UUID.randomUUID(), RagIndexOperation.UPSERT).pointId(-1));
        assertThatIllegalStateException().isThrownBy(
                () -> job(1, UUID.randomUUID(), RagIndexOperation.DELETE).pointId(0));
    }

    private ClaimedJob job(long id, UUID attempt, RagIndexOperation operation) {
        return new ClaimedJob(id, attempt, new RagSourceKey(RagSourceType.COMPANY, 1L),
                1, operation, null, 1);
    }
}
