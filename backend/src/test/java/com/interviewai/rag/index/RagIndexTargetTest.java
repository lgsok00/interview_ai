package com.interviewai.rag.index;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RagIndexTargetTest {

    @Test
    @DisplayName("색인 설정 버전을 정규화하고 원본 스냅샷을 유지한다")
    void normalizePipelineVersion() {
        RagSourceSnapshot snapshot = snapshot();
        RagIndexTarget target = new RagIndexTarget(snapshot, "  pipeline-v1  ");

        assertThat(target.snapshot()).isSameAs(snapshot);
        assertThat(target.pipelineVersion()).isEqualTo("pipeline-v1");
        assertThat(target).isNotEqualTo(new RagIndexTarget(snapshot, "pipeline-v2"));
    }

    @Test
    @DisplayName("색인 설정 버전의 1자와 100자 경계를 허용한다")
    void acceptVersionLengthBoundaries() {
        assertThat(new RagIndexTarget(snapshot(), "v").pipelineVersion()).isEqualTo("v");
        assertThat(new RagIndexTarget(snapshot(), " " + "v".repeat(100) + " ")
                .pipelineVersion()).hasSize(100);
    }

    @Test
    @DisplayName("누락된 스냅샷과 설정 버전 및 빈 값과 101자 버전을 거부한다")
    void rejectInvalidTarget() {
        assertThatNullPointerException().isThrownBy(() -> new RagIndexTarget(null, "v1"));
        assertThatNullPointerException().isThrownBy(() -> new RagIndexTarget(snapshot(), null));
        for (String version : new String[]{"", " \n\t ", "v".repeat(101)}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RagIndexTarget(snapshot(), version));
        }
    }

    private RagSourceSnapshot snapshot() {
        return new RagSourceSnapshot(new RagSourceKey(RagSourceType.COMPANY, 10L),
                null, 10L, "기업", "기업 설명", "revision-1");
    }
}
