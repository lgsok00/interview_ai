package com.interviewai.coverletter.draft;

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
class CoverLetterDraftSchedulerTest {

    @Mock
    CoverLetterDraftWorker worker;

    CoverLetterDraftScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CoverLetterDraftScheduler(worker,
                new CoverLetterDraftProperties(true, "test-model", Duration.ofSeconds(1), Duration.ZERO, 3));
    }

    @Test
    void scheduledMethodUsesConfiguredDelayProperties() throws Exception {
        Scheduled scheduled = CoverLetterDraftScheduler.class.getDeclaredMethod("process").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${cover-letter.draft.fixed-delay:1s}");
        assertThat(scheduled.initialDelayString()).isEqualTo("${cover-letter.draft.initial-delay:5s}");
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
            boolean interrupted = Thread.interrupted();
            assertThat(interrupted).isTrue();
        }
    }
}
