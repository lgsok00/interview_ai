package com.interviewai.interview.entity;

import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.QuestionGenerationSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class InterviewAnswerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 0, 0);

    @Test
    void normalizesAnswerAndPreservesInternalWhitespace() {
        var question = parent();
        var answer = InterviewAnswer.create(question, " \n첫 줄\n  둘째 줄\t", NOW);
        assertThat(answer.getContent()).isEqualTo("첫 줄\n  둘째 줄");
        assertThat(answer.getQuestion()).isSameAs(question);
        assertThat(answer.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10000})
    void acceptsLengthBoundaries(int length) {
        assertThat(InterviewAnswer.create(parent(), "가".repeat(length), NOW).getContent()).hasSize(length);
    }

    @Test
    void rejectsEmptyOversizedAndMissingValues() {
        for (String content : new String[]{"", " \n\t", "가".repeat(10001), "😀".repeat(5001)}) {
            assertThatIllegalArgumentException().isThrownBy(() -> InterviewAnswer.create(parent(), content, NOW));
        }
        assertThatNullPointerException().isThrownBy(() -> InterviewAnswer.create(parent(), null, NOW));
        assertThatNullPointerException().isThrownBy(() -> InterviewAnswer.create(null, "답변", NOW));
        assertThatNullPointerException().isThrownBy(() -> InterviewAnswer.create(parent(), "답변", null));
        assertThat(InterviewAnswer.create(parent(), "😀".repeat(5000), NOW).getContent()).hasSize(10000);
    }

    @Test
    void createsFollowUpInParentsSessionAndRejectsFurtherDepth() {
        var parent = parent();
        var followUp = InterviewQuestion.createFollowUp(parent, 6, QuestionGenerationSource.AI, "꼬리 질문", "context", NOW);
        assertThat(followUp.getParentQuestionId()).isEqualTo(10L);
        assertThat(followUp.getSession()).isSameAs(parent.getSession());
        assertThat(followUp.getQuestionType()).isEqualTo(InterviewQuestionType.FOLLOW_UP);
        assertThat(followUp.getSequenceNumber()).isEqualTo(6);
        ReflectionTestUtils.setField(followUp, "id", 11L);
        assertThatIllegalArgumentException().isThrownBy(() -> InterviewQuestion.createFollowUp(
                followUp, 7, QuestionGenerationSource.AI, "질문", null, NOW));
        ReflectionTestUtils.setField(parent, "id", null);
        assertThatIllegalArgumentException().isThrownBy(() -> InterviewQuestion.createFollowUp(
                parent, 6, QuestionGenerationSource.AI, "질문", null, NOW));
    }

    private InterviewQuestion parent() {
        var question = InterviewQuestion.create(mock(InterviewSession.class), 1, InterviewQuestionType.TECHNICAL,
                QuestionGenerationSource.AI, "부모 질문", null, NOW);
        ReflectionTestUtils.setField(question, "id", 10L);
        return question;
    }
}
