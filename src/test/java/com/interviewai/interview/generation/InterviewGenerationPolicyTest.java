package com.interviewai.interview.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static com.interviewai.interview.enums.InterviewQuestionType.*;
import static com.interviewai.interview.enums.QuestionGenerationSource.AI;
import static com.interviewai.interview.enums.QuestionGenerationSource.FALLBACK;
import static com.interviewai.interview.generation.InterviewGenerationPolicy.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewGenerationPolicyTest {
    @Test
    void createsImmutableFallbackWithExpectedTypesAndNoContext() {
        Batch batch = fallback("RAG_EMPTY");
        assertThat(batch.source()).isEqualTo(FALLBACK);
        assertThat(batch.contextSnapshot()).isNull();
        assertThat(batch.fallbackReason()).isEqualTo("RAG_EMPTY");
        assertThat(batch.questions()).extracting(Question::type)
                .containsExactly(TECHNICAL, TECHNICAL, TECHNICAL, BEHAVIORAL, BEHAVIORAL);
        assertThatThrownBy(() -> batch.questions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 1000})
    void acceptsInclusiveCodePointLength(int length) {
        var questions = questions();
        questions.set(0, new Question(TECHNICAL, "😀".repeat(length)));
        assertThat(validate(questions)).hasSize(5);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 19, 1001})
    void rejectsInvalidLength(int length) {
        var questions = questions();
        questions.set(0, new Question(TECHNICAL, "가".repeat(length)));
        assertThatThrownBy(() -> validate(questions)).isInstanceOf(GenerationException.class);
    }

    @Test
    void rejectsNormalizedDuplicates() {
        var questions = questions();
        questions.set(0, new Question(TECHNICAL, "ＡＢＣ  explain your design choices carefully"));
        questions.set(1, new Question(TECHNICAL, "abc\texplain your design choices carefully"));
        assertThatThrownBy(() -> validate(questions)).isInstanceOf(GenerationException.class);
    }

    @Test
    void rejectsMissingQuestionsFollowUpsAndWrongDistribution() {
        // null 입력이 거부되는지 확인하는 의도적인 실패 테스트
        // noinspection DataFlowIssue
        assertThatThrownBy(() -> validate(null)).isInstanceOf(GenerationException.class);
        assertThatThrownBy(() -> validate(List.of())).isInstanceOf(GenerationException.class);
        var questions = questions();
        questions.set(0, null);
        assertThatThrownBy(() -> validate(questions)).isInstanceOf(GenerationException.class);
        questions.set(0, new Question(FOLLOW_UP, "후속 질문을 생성하면 안 되는 초기 면접 질문입니다."));
        assertThatThrownBy(() -> validate(questions)).isInstanceOf(GenerationException.class);
        questions.set(0, new Question(BEHAVIORAL, "유형별 개수가 올바르지 않은 초기 면접 질문입니다."));
        assertThatThrownBy(() -> validate(questions)).isInstanceOf(GenerationException.class);
    }

    @Test
    void enforcesSourceAndContextContract() {
        assertThatThrownBy(() -> new Batch(questions(), AI, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Batch(questions(), AI, "{}", "RAG_EMPTY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Batch(questions(), FALLBACK, "{}", "RAG_EMPTY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fallback(" ")).isInstanceOf(IllegalArgumentException.class);
        var original = questions();
        Batch batch = new Batch(original, AI, "{}", null);
        original.clear();
        assertThat(batch.questions()).hasSize(5);
    }

    private ArrayList<Question> questions() {
        return new ArrayList<>(fallback("TEST").questions());
    }
}
