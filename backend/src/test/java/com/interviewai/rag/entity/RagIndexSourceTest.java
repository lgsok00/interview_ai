package com.interviewai.rag.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RagIndexSourceTest {

    @Test
    void retainsActiveGenerationUntilLatestSequenceIsPublished() {
        var source = new RagIndexSource();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        source.allocateNextSequence();
        assertThat(source.activate(1, first)).isTrue();
        source.allocateNextSequence();
        assertThat(source.getActiveGenerationId()).isEqualTo(first.toString());
        assertThat(source.activate(1, UUID.randomUUID())).isFalse();
        assertThat(source.activate(3, UUID.randomUUID())).isFalse();
        assertThat(source.getActiveSequence()).isEqualTo(1);
        assertThat(source.activate(2, second)).isTrue();
        assertThat(source.getActiveGenerationId()).isEqualTo(second.toString());
        assertThat(source.getActiveSequence()).isEqualTo(2);
    }

    @Test
    void deleteClearsActiveGenerationAndOnlyLaterSequenceCanReactivate() {
        var source = new RagIndexSource();
        source.allocateNextSequence();
        source.activate(1, UUID.randomUUID());
        source.allocateNextSequence();
        source.recordDelete(2);
        source.recordDelete(2);
        assertThat(source.getActiveGenerationId()).isNull();
        assertThat(source.getActiveSequence()).isZero();
        assertThat(source.getTombstoneSequence()).isEqualTo(2);
        assertThat(source.activate(1, UUID.randomUUID())).isFalse();
        assertThat(source.activate(2, UUID.randomUUID())).isFalse();
        source.allocateNextSequence();
        assertThat(source.activate(3, UUID.randomUUID())).isTrue();
        assertThat(source.getTombstoneSequence()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1, 3})
    void invalidDeletePreservesExistingPublication(long sequence) {
        var source = new RagIndexSource();
        UUID generation = UUID.randomUUID();
        source.allocateNextSequence();
        source.activate(1, generation);
        source.allocateNextSequence();
        assertThatIllegalArgumentException().isThrownBy(() -> source.recordDelete(sequence));
        assertThat(source.getActiveGenerationId()).isEqualTo(generation.toString());
        assertThat(source.getActiveSequence()).isEqualTo(1);
        assertThat(source.getTombstoneSequence()).isZero();
    }

    @Test
    void invalidActivationPreservesExistingPublication() {
        var source = new RagIndexSource();
        UUID generation = UUID.randomUUID();
        source.allocateNextSequence();
        source.activate(1, generation);
        assertThatIllegalArgumentException().isThrownBy(() -> source.activate(0, generation));
        assertThatIllegalArgumentException().isThrownBy(() -> source.activate(-1, generation));
        assertThatNullPointerException().isThrownBy(() -> source.activate(1, null));
        assertThat(source.getActiveGenerationId()).isEqualTo(generation.toString());
        assertThat(source.getActiveSequence()).isEqualTo(1);
    }

    @Test
    void supportsPublicationAndTombstoneAtMaximumSequence() {
        var source = new RagIndexSource();
        ReflectionTestUtils.setField(source, "lastSequence", Long.MAX_VALUE);
        assertThat(source.activate(Long.MAX_VALUE, UUID.randomUUID())).isTrue();
        source.recordDelete(Long.MAX_VALUE);
        assertThat(source.getTombstoneSequence()).isEqualTo(Long.MAX_VALUE);
        assertThat(source.activate(Long.MAX_VALUE, UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("초기 순번 0에서 시작하여 호출마다 순번을 증가시킨다")
    void incrementsSequence() {
        RagIndexSource source = new RagIndexSource();

        assertThat(source.getLastSequence()).isZero();
        assertThat(source.allocateNextSequence()).isEqualTo(1L);
        assertThat(source.allocateNextSequence()).isEqualTo(2L);
        assertThat(source.getLastSequence()).isEqualTo(2L);
    }

    @Test
    @DisplayName("최대 순번은 허용하고 초과 시 예외가 발생해도 엔티티 값을 보존한다")
    void preservesSequenceOnOverflow() {
        RagIndexSource source = new RagIndexSource();
        ReflectionTestUtils.setField(source, "lastSequence", Long.MAX_VALUE - 1);

        assertThat(source.allocateNextSequence()).isEqualTo(Long.MAX_VALUE);
        assertThatThrownBy(source::allocateNextSequence).isInstanceOf(ArithmeticException.class);
        assertThat(source.getLastSequence()).isEqualTo(Long.MAX_VALUE);
    }
}
