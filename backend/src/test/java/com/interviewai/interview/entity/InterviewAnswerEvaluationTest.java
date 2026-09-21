package com.interviewai.interview.entity;

import com.interviewai.interview.enums.AnswerEvaluationStatus;
import com.interviewai.interview.generation.InterviewGenerationPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class InterviewAnswerEvaluationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 0, 0);
    private static final String FIRST = "00000000-0000-0000-0000-000000000001";
    private static final String SECOND = "00000000-0000-0000-0000-000000000002";

    @Test
    void storesScoresAndFeedbackAndClearsLeaseOnSuccess() {
        var evaluation = evaluation();
        evaluation.claim(FIRST, NOW, NOW.plusSeconds(120));
        complete(evaluation, FIRST, NOW.plusSeconds(1));
        assertThat(evaluation.getStatus()).isEqualTo(AnswerEvaluationStatus.COMPLETED);
        assertThat(evaluation.getStarScore()).isZero();
        assertThat(evaluation.getLogicScore()).isEqualTo(100);
        assertThat(evaluation.getImprovements()).isEqualTo("개선사항");
        assertThat(evaluation.getAttemptId()).isNull();
        assertThat(evaluation.getLeaseExpiresAt()).isNull();
        assertThat(evaluation.getCompletedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void rejectsCompletionAtExactExpiryAndOldAttemptAfterReclaim() {
        var evaluation = evaluation();
        evaluation.claim(FIRST, NOW, NOW.plusSeconds(120));
        assertThatIllegalStateException().isThrownBy(() -> complete(evaluation, FIRST, NOW.plusSeconds(120)));
        evaluation.expireLease("LEASE_EXPIRED", NOW.plusSeconds(130), NOW.plusSeconds(120));
        assertThatIllegalStateException().isThrownBy(() -> evaluation.claim(SECOND, NOW.plusSeconds(129), NOW.plusSeconds(250)));
        evaluation.claim(SECOND, NOW.plusSeconds(130), NOW.plusSeconds(250));
        assertThatIllegalStateException().isThrownBy(() -> complete(evaluation, FIRST, NOW.plusSeconds(131)));
        assertThatIllegalStateException().isThrownBy(() -> evaluation.fail(FIRST, "OLD_FAILURE", NOW.plusSeconds(131)));
        complete(evaluation, SECOND, NOW.plusSeconds(131));
        assertThat(evaluation.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void exhaustsAutomaticAttemptsAndBoundsManualRetries() {
        var evaluation = evaluation();
        for (int cycle = 0; cycle < 3; cycle++) {
            LocalDateTime start = NOW.plusHours(cycle);
            for (int attempt = 0; attempt < 3; attempt++) {
                LocalDateTime time = start.plusMinutes(attempt * 3L);
                evaluation.claim(FIRST, time, time.plusSeconds(120));
                evaluation.expireLease("LEASE_EXPIRED", time.plusSeconds(121), time.plusSeconds(120));
            }
            assertThat(evaluation.getStatus()).isEqualTo(AnswerEvaluationStatus.FAILED);
            if (cycle < 2) {
                evaluation.manualRetry(NOW.plusHours(cycle + 1));
                assertThat(evaluation.getAttemptCount()).isZero();
            }
        }
        assertThat(evaluation.getManualRetryCount()).isEqualTo(2);
        assertThatIllegalStateException().isThrownBy(() -> evaluation.manualRetry(NOW.plusHours(3)));
    }

    @Test
    void rejectsInvalidResultWithoutPartiallyChangingScores() {
        var evaluation = evaluation();
        evaluation.claim(FIRST, NOW, NOW.plusSeconds(120));
        assertThatIllegalArgumentException().isThrownBy(() -> evaluation.complete(
                FIRST, 50, 60, 70, "강점", "개선사항", " ", "context", NOW.plusSeconds(1)));
        assertThat(evaluation.getStarScore()).isNull();
        assertThat(evaluation.getStatus()).isEqualTo(AnswerEvaluationStatus.PROCESSING);
        assertThat(evaluation.getAttemptId()).isEqualTo(FIRST);
    }

    private InterviewAnswerEvaluation evaluation() {
        return InterviewAnswerEvaluation.create(mock(InterviewAnswer.class),
                InterviewGenerationPolicy.Mode.AI, "test-model", "answer-evaluation-v1", NOW);
    }

    private void complete(InterviewAnswerEvaluation evaluation, String attemptId, LocalDateTime now) {
        evaluation.complete(attemptId, 0, 100, 50, "강점", "개선사항", "예시 답변", "context", now);
    }
}
