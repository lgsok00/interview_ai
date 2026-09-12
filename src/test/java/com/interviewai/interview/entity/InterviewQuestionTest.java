package com.interviewai.interview.entity;

import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.QuestionGenerationSource;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class InterviewQuestionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 10, 0);


    @Test
    @DisplayName("AI가 생성한 기술 질문과 RAG context를 만든다")
    void createsAiQuestionWithContext() {
        InterviewSession session = session();

        InterviewQuestion question = InterviewQuestion.create(
                session,
                1,
                InterviewQuestionType.TECHNICAL,
                QuestionGenerationSource.AI,
                "대규모 트래픽을 어떻게 처리하시겠습니까?",
                "채용공고 및 이력서 context",
                NOW
        );

        assertThat(question.getSession()).isSameAs(session);
        assertThat(question.getSequenceNumber()).isEqualTo(1);
        assertThat(question.getQuestionType()).isEqualTo(InterviewQuestionType.TECHNICAL);
        assertThat(question.getGenerationSource()).isEqualTo(QuestionGenerationSource.AI);
        assertThat(question.getContextSnapshot()).isEqualTo("채용공고 및 이력서 context");
        assertThat(question.getCreatedAt()).isEqualTo(NOW);
    }


    @Test
    @DisplayName("공백 RAG context는 없는 값으로 정규화한다")
    void normalizesBlankContextToNull() {
        InterviewQuestion question = InterviewQuestion.create(
                session(), 1, InterviewQuestionType.BEHAVIORAL,
                QuestionGenerationSource.FALLBACK, "협업 경험을 설명해 주세요.", "  ", NOW
        );

        assertThat(question.getContextSnapshot()).isNull();
    }


    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("0 이하 질문 순서를 거부한다")
    void rejectsNonPositiveSequence(int sequence) {
        assertThatIllegalArgumentException().isThrownBy(() -> InterviewQuestion.create(
                session(), sequence, InterviewQuestionType.TECHNICAL,
                QuestionGenerationSource.AI, "질문", null, NOW
        ));
    }


    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    @DisplayName("빈 질문 본문을 거부한다")
    void rejectsMissingContent(String content) {
        if (content == null) {
            assertThatNullPointerException().isThrownBy(() -> InterviewQuestion.create(
                    session(), 1, InterviewQuestionType.TECHNICAL,
                    QuestionGenerationSource.AI, null, null, NOW
            ));
            return;
        }

        assertThatIllegalArgumentException().isThrownBy(() -> InterviewQuestion.create(
                session(), 1, InterviewQuestionType.TECHNICAL,
                QuestionGenerationSource.AI, content, null, NOW
        ));
    }


    @Test
    @DisplayName("필수 연관과 enum 및 생성 시각의 누락을 거부한다")
    void rejectsMissingRequiredValues() {
        assertThatNullPointerException().isThrownBy(() -> InterviewQuestion.create(
                null, 1, InterviewQuestionType.TECHNICAL,
                QuestionGenerationSource.AI, "질문", null, NOW
        ));
        assertThatNullPointerException().isThrownBy(() -> InterviewQuestion.create(
                session(), 1, null, QuestionGenerationSource.AI, "질문", null, NOW
        ));
        assertThatNullPointerException().isThrownBy(() -> InterviewQuestion.create(
                session(), 1, InterviewQuestionType.TECHNICAL, null, "질문", null, NOW
        ));
        assertThatNullPointerException().isThrownBy(() -> InterviewQuestion.create(
                session(), 1, InterviewQuestionType.TECHNICAL,
                QuestionGenerationSource.AI, "질문", null, null
        ));
    }


    private InterviewSession session() {
        User user = User.createLocalUser("user@example.com", "{bcrypt}encoded", "사용자");
        return InterviewSession.create(
                user, 1L, null, null,
                "회사", "백엔드 개발자", "Backend", "공고 본문",
                null, null, null, null, NOW
        );
    }
}
