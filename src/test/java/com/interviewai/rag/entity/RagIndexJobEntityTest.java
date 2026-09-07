package com.interviewai.rag.entity;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class RagIndexJobEntityTest {

    private static final RagSourceKey KEY = new RagSourceKey(RagSourceType.RESUME, 1L);
    private static final Instant NOW = Instant.parse("2026-09-07T01:02:03.123456789Z");

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveSequence(long sequence) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                RagIndexJobEntity.delete(KEY, sequence, 1, NOW));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveMaxAttempts(int maxAttempts) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                RagIndexJobEntity.delete(KEY, 1, maxAttempts, NOW));
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThatNullPointerException().isThrownBy(() ->
                RagIndexJobEntity.delete(null, 1, 1, NOW));
        assertThatNullPointerException().isThrownBy(() ->
                RagIndexJobEntity.delete(KEY, 1, 1, null));
        assertThatNullPointerException().isThrownBy(() ->
                RagIndexJobEntity.upsert(null, 1, 1, NOW));
    }

    @Test
    void acceptsMaximumSequenceAndTruncatesTimeToDatabasePrecision() {
        RagIndexJobEntity job = RagIndexJobEntity.delete(KEY, Long.MAX_VALUE, 1, NOW);
        assertThat(job.getSourceSequence()).isEqualTo(Long.MAX_VALUE);
        assertThat(job.getCreatedAt()).isEqualTo(Instant.parse("2026-09-07T01:02:03.123456Z"));
        assertThat(job.getUpdatedAt()).isEqualTo(job.getCreatedAt());
    }
}
