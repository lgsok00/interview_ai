package com.interviewai.rag.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RagIndexingPropertiesTest {

    @Test
    void acceptsValidIndexingConfiguration() {
        assertThatCode(() -> createProperties(Duration.ofSeconds(1), Duration.ZERO, 20, 700, 100, 10000, 20))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrNonPositiveFixedDelay() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> createProperties(null, Duration.ZERO, 20, 700, 100, 10000, 20));
        assertThatIllegalArgumentException().isThrownBy(
                () -> createProperties(Duration.ZERO, Duration.ZERO, 20, 700, 100, 10000, 20));
        assertThatIllegalArgumentException().isThrownBy(
                () -> createProperties(Duration.ofSeconds(-1), Duration.ZERO, 20, 700, 100, 10000, 20));
    }

    @Test
    void acceptsZeroInitialDelayAndRejectsMissingOrNegativeValue() {
        assertThatCode(() -> createProperties(Duration.ofSeconds(1), Duration.ZERO, 20, 700, 100, 10000, 20))
                .doesNotThrowAnyException();
        assertThatIllegalArgumentException().isThrownBy(
                () -> createProperties(Duration.ofSeconds(1), null, 20, 700, 100, 10000, 20));
        assertThatIllegalArgumentException().isThrownBy(
                () -> createProperties(Duration.ofSeconds(1), Duration.ofMillis(-1), 20, 700, 100, 10000, 20));
    }

    @Test
    void rejectsInvalidChunkAndBatchBoundaries() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> createProperties(Duration.ofSeconds(1), Duration.ZERO, 0, 700, 100, 10000, 20));
        assertThatIllegalArgumentException().isThrownBy(
                () -> createProperties(Duration.ofSeconds(1), Duration.ZERO, 20, 0, 0, 10000, 20));
        assertThatIllegalArgumentException().isThrownBy(
                () -> createProperties(Duration.ofSeconds(1), Duration.ZERO, 20, 700, -1, 10000, 20));
        assertThatIllegalArgumentException().isThrownBy(
                () -> createProperties(Duration.ofSeconds(1), Duration.ZERO, 20, 700, 700, 10000, 20));
        assertThatIllegalArgumentException().isThrownBy(
                () -> createProperties(Duration.ofSeconds(1), Duration.ZERO, 20, 700, 100, 0, 20));
        assertThatIllegalArgumentException().isThrownBy(
                () -> createProperties(Duration.ofSeconds(1), Duration.ZERO, 20, 700, 100, 10000, 0));
    }

    private void createProperties(
            Duration fixedDelay,
            Duration initialDelay,
            int maxJobsPerRun,
            int chunkSize,
            int chunkOverlap,
            int maxChunks,
            int writeBatchSize
    ) {
        new RagIndexingProperties(
                true,
                fixedDelay,
                initialDelay,
                maxJobsPerRun,
                chunkSize,
                chunkOverlap,
                maxChunks,
                writeBatchSize
        );
    }
}
