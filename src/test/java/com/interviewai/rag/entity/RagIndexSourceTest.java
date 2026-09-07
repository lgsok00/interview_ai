package com.interviewai.rag.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class RagIndexSourceTest {

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
