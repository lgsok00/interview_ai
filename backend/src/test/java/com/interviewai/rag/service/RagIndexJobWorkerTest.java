package com.interviewai.rag.service;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.service.RagIndexJobExecutionService.ClaimedJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;
import java.util.UUID;

import static com.interviewai.rag.service.RagIndexJobWorker.RunResult.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagIndexJobWorkerTest {

    @Mock private RagIndexJobExecutionService service;
    private RagIndexJobWorker worker;
    private final ClaimedJob job = new ClaimedJob(1L, UUID.randomUUID(),
            new RagSourceKey(RagSourceType.COMPANY, 10L), 1L,
            RagIndexOperation.DELETE, null, 1);

    @BeforeEach
    void setUp() {
        worker = new RagIndexJobWorker(service);
    }

    @Test
    void emptyQueueDoesNotCallProcessor() {
        when(service.claimNext()).thenReturn(Optional.empty());
        assertThat(worker.runOnce((claim, renew) -> fail("처리기가 호출되면 안 됩니다.")))
                .isEqualTo(NO_JOB);
        verify(service).claimNext();
        verifyNoMoreInteractions(service);
    }

    @Test
    void nullProcessorDoesNotConsumeJob() {
        assertThatNullPointerException().isThrownBy(() -> worker.runOnce(null));
        verifyNoInteractions(service);
    }

    @Test
    void passesClaimAndRenewalCallbackBeforeRecordingSuccess() {
        when(service.claimNext()).thenReturn(Optional.of(job));
        when(service.renew(job.jobId(), job.attemptId())).thenReturn(true);
        when(service.succeed(job.jobId(), job.attemptId())).thenReturn(true);
        assertThat(worker.runOnce((claim, renew) -> {
            assertThat(claim).isEqualTo(job);
            assertThat(renew.getAsBoolean()).isTrue();
        })).isEqualTo(SUCCEEDED);
        var order = inOrder(service);
        order.verify(service).claimNext();
        order.verify(service).renew(job.jobId(), job.attemptId());
        order.verify(service).succeed(job.jobId(), job.attemptId());
        verifyNoMoreInteractions(service);
    }

    @Test
    void lateCompletionReportsLeaseLost() {
        when(service.claimNext()).thenReturn(Optional.of(job));
        assertThat(worker.runOnce((claim, renew) -> {})).isEqualTo(LEASE_LOST);
        verify(service).succeed(job.jobId(), job.attemptId());
    }

    @Test
    void processorExceptionStoresOnlyFailureCode() {
        when(service.claimNext()).thenReturn(Optional.of(job));
        when(service.fail(job.jobId(), job.attemptId(), "PROCESSING_FAILED")).thenReturn(true);
        assertThat(worker.runOnce((claim, renew) -> {
            throw new IllegalStateException("민감한 원본 본문");
        })).isEqualTo(FAILURE_RECORDED);
        verify(service).fail(job.jobId(), job.attemptId(), "PROCESSING_FAILED");
        verify(service, never()).succeed(anyLong(), any());
    }

    @Test
    void lateFailureReportsLeaseLost() {
        when(service.claimNext()).thenReturn(Optional.of(job));
        assertThat(worker.runOnce((claim, renew) -> {
            throw new IllegalStateException("failed");
        })).isEqualTo(LEASE_LOST);
        verify(service).fail(job.jobId(), job.attemptId(), "PROCESSING_FAILED");
    }

    @Test
    void interruptionRestoresThreadFlagAndRecordsFailure() {
        when(service.claimNext()).thenReturn(Optional.of(job));
        when(service.fail(job.jobId(), job.attemptId(), "PROCESSING_INTERRUPTED")).thenReturn(true);
        try {
            assertThat(worker.runOnce((claim, renew) -> {
                throw new InterruptedException("interrupted");
            })).isEqualTo(FAILURE_RECORDED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Test
    void completionDatabaseFailurePropagatesWithoutRecordingProcessingFailure() {
        when(service.claimNext()).thenReturn(Optional.of(job));
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(service.succeed(job.jobId(), job.attemptId())).thenThrow(failure);
        assertThatThrownBy(() -> worker.runOnce((claim, renew) -> {})).isSameAs(failure);
        verify(service, never()).fail(anyLong(), any(), anyString());
    }

    @Test
    void claimDatabaseFailureDoesNotInvokeProcessor() {
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(service.claimNext()).thenThrow(failure);
        assertThatThrownBy(() -> worker.runOnce((claim, renew) -> fail("호출 금지")))
                .isSameAs(failure);
        verify(service).claimNext();
        verifyNoMoreInteractions(service);
    }

    @Test
    void fatalErrorLeavesRecoveryToLeaseExpiry() {
        when(service.claimNext()).thenReturn(Optional.of(job));
        var failure = new AssertionError("fatal");
        assertThatThrownBy(() -> worker.runOnce((claim, renew) -> { throw failure; }))
                .isSameAs(failure);
        verify(service, never()).fail(anyLong(), any(), anyString());
        verify(service, never()).succeed(anyLong(), any());
    }
}
