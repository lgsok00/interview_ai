package com.interviewai.rag.scheduler;

import com.interviewai.rag.config.RagIndexingProperties;
import com.interviewai.rag.service.QdrantRagIndexProcessor;
import com.interviewai.rag.service.RagIndexJobWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

import static com.interviewai.rag.service.RagIndexJobWorker.RunResult.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagIndexJobSchedulerTest {

    @Mock
    private RagIndexJobWorker worker;
    @Mock
    private QdrantRagIndexProcessor processor;

    private RagIndexJobScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RagIndexJobScheduler(worker, processor, properties());
    }

    @Test
    void scheduledMethodUsesConfiguredDelayProperties() throws Exception {
        Scheduled scheduled = RagIndexJobScheduler.class
                .getDeclaredMethod("processPendingJobs")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${rag.indexing.fixed-delay}");
        assertThat(scheduled.initialDelayString()).isEqualTo("${rag.indexing.initial-delay}");
    }

    @Test
    void drainsJobsUntilQueueIsEmpty() {
        when(worker.runOnce(processor)).thenReturn(SUCCEEDED, FAILURE_RECORDED, NO_JOB);

        scheduler.processPendingJobs();

        verify(worker, times(3)).runOnce(processor);
    }

    @Test
    void stopsAtPerRunLimitWithoutExtraQueueProbe() {
        when(worker.runOnce(processor)).thenReturn(SUCCEEDED, LEASE_LOST, FAILURE_RECORDED);

        scheduler.processPendingJobs();

        verify(worker, times(3)).runOnce(processor);
    }

    @Test
    void stopsCurrentRunWhenWorkerInfrastructureFails() {
        when(worker.runOnce(processor))
                .thenReturn(SUCCEEDED)
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> scheduler.processPendingJobs()).doesNotThrowAnyException();
        verify(worker, times(2)).runOnce(processor);
    }

    @Test
    void emptyQueueReturnsAfterSingleProbe() {
        when(worker.runOnce(processor)).thenReturn(NO_JOB);

        scheduler.processPendingJobs();

        verify(worker).runOnce(processor);
        verifyNoMoreInteractions(worker);
    }

    private RagIndexingProperties properties() {
        return new RagIndexingProperties(
                true,
                Duration.ofSeconds(1),
                Duration.ZERO,
                3,
                700,
                100,
                10000,
                20
        );
    }
}
