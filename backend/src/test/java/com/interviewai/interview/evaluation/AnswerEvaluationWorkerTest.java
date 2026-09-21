package com.interviewai.interview.evaluation;

import com.interviewai.interview.generation.InterviewGenerationDeadline;
import com.interviewai.interview.generation.InterviewGenerationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnswerEvaluationWorkerTest {
    private final AnswerEvaluationExecutionService execution = mock(AnswerEvaluationExecutionService.class);
    private final AnswerEvaluationGenerator generator = mock(AnswerEvaluationGenerator.class);
    private final InterviewGenerationDeadline deadline = mock(InterviewGenerationDeadline.class);
    private final AnswerEvaluationWorker worker = new AnswerEvaluationWorker(execution, generator, deadline);
    private final AnswerEvaluationExecutionService.Claim claim = new AnswerEvaluationExecutionService.Claim(
            1, "attempt", InterviewGenerationPolicy.Mode.AI, "model", AnswerEvaluationPolicy.PIPELINE_VERSION,
            new AnswerEvaluationGenerator.Input("role", "title", "content", "question", "answer"));

    @Test
    void recoversExpiredWorkBeforeClaimingAndStopsOnEmptyQueue() {
        when(execution.recoverOne()).thenReturn(true);
        assertThat(worker.runOnce()).isTrue();
        verify(execution, never()).claimNext();
        when(execution.recoverOne()).thenReturn(false);
        when(execution.claimNext()).thenReturn(Optional.empty());
        assertThat(worker.runOnce()).isFalse();
        verifyNoInteractions(generator, deadline);
    }

    @Test
    void storesSuccessfulOutput() throws Exception {
        setup();
        var result = new AnswerEvaluationGenerator.Generated(new AnswerEvaluationPolicy.Result(
                50, 60, 70, "강".repeat(20), "개".repeat(20), "답변"), "context");
        when(generator.evaluate(claim.input(), claim.mode(), claim.model(), claim.pipelineVersion())).thenReturn(result);
        assertThat(worker.runOnce()).isTrue();
        verify(execution).complete(claim, result);
        verify(execution, never()).fail(any(), anyString(), anyBoolean());
    }

    @Test
    void processedFailureAllowsSchedulerToContinueOtherJobs() throws Exception {
        setup();
        when(generator.evaluate(any(), any(), anyString(), anyString())).thenThrow(
                new AnswerEvaluationPolicy.EvaluationException("ANSWER_EVALUATION_INVALID_OUTPUT", true));
        assertThat(worker.runOnce()).isTrue();
        verify(execution).fail(claim, "ANSWER_EVALUATION_INVALID_OUTPUT", true);
        verify(execution, never()).complete(any(), any());
    }

    @Test
    void databaseFailureIsNotConvertedToAiFailure() throws Exception {
        setup();
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(generator.evaluate(any(), any(), anyString(), anyString())).thenThrow(failure);
        assertThatThrownBy(worker::runOnce).isSameAs(failure);
        verify(execution, never()).fail(any(), anyString(), anyBoolean());
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    void interruptionLeavesLeaseForRecoveryAndPreservesInterrupt() throws Exception {
        when(execution.claimNext()).thenReturn(Optional.of(claim));
        when(deadline.call(any(), any(), anyString())).thenThrow(new InterruptedException());
        try {
            assertThat(worker.runOnce()).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(execution, never()).fail(any(), anyString(), anyBoolean());
        } finally {
            Thread.interrupted();
        }
    }

    private void setup() throws Exception {
        when(execution.claimNext()).thenReturn(Optional.of(claim));
        when(deadline.call(any(), any(), anyString())).thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call());
    }
}
