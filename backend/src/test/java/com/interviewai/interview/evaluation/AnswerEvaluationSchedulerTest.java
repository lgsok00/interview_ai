package com.interviewai.interview.evaluation;

import com.interviewai.interview.generation.InterviewGenerationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnswerEvaluationSchedulerTest {

    @Mock
    AnswerEvaluationWorker worker;

    AnswerEvaluationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AnswerEvaluationScheduler(worker, properties());
    }

    @Test
    void scheduledMethodUsesConfiguredDelayProperties() throws Exception {
        Scheduled scheduled = AnswerEvaluationScheduler.class.getDeclaredMethod("process").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${interview.evaluation.fixed-delay:1s}");
        assertThat(scheduled.initialDelayString()).isEqualTo("${interview.evaluation.initial-delay:5s}");
    }

    @Test
    void drainsJobsUntilQueueIsEmpty() {
        when(worker.runOnce()).thenReturn(true, true, false);

        scheduler.process();

        verify(worker, times(3)).runOnce();
        verifyNoMoreInteractions(worker);
    }

    @Test
    void stopsAtPerRunLimitWithoutExtraQueueProbe() {
        when(worker.runOnce()).thenReturn(true);

        scheduler.process();

        verify(worker, times(3)).runOnce();
    }

    @Test
    void stopsCurrentRunWhenWorkerInfrastructureFails() {
        when(worker.runOnce()).thenReturn(true).thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(scheduler::process).doesNotThrowAnyException();

        verify(worker, times(2)).runOnce();
    }

    @Test
    void interruptedThreadDoesNotClaimWorkAndPreservesInterrupt() {
        Thread.currentThread().interrupt();
        try {
            scheduler.process();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(worker);
        } finally {
            // 다음 테스트에 영향을 주지 않도록 현재 스레드의 인터럽트 상태를 초기화한다.
            // noinspection ResultOfMethodCallIgnored
            Thread.interrupted();
        }
    }

    private AnswerEvaluationProperties properties() {
        return new AnswerEvaluationProperties(
                true,
                InterviewGenerationPolicy.Mode.FALLBACK_ONLY,
                "",
                Duration.ofSeconds(1),
                Duration.ZERO,
                3
        );
    }
}
