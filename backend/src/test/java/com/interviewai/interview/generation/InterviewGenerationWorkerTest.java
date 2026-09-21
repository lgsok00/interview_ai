package com.interviewai.interview.generation;

import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.search.RagSearchResult;
import com.openai.errors.OpenAIServiceException;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.interviewai.interview.generation.InterviewGenerationPolicy.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class InterviewGenerationWorkerTest {
    private final InterviewGenerationExecutionService execution = mock(InterviewGenerationExecutionService.class);
    private final InterviewGenerationInputService inputs = mock(InterviewGenerationInputService.class);
    private final InterviewGenerationDeadline deadline = mock(InterviewGenerationDeadline.class);
    private final InterviewChatQuestionGenerator generator = mock(InterviewChatQuestionGenerator.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<InterviewChatQuestionGenerator> provider = mock(ObjectProvider.class);
    private InterviewGenerationWorker worker;

    @BeforeEach
    void setUp() throws Exception {
        worker = new InterviewGenerationWorker(execution, inputs, deadline, provider);
        lenient().when(deadline.call(any(), any(Duration.class), anyString()))
                .thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call());
    }

    @Test
    void noJobDoesNotInvokeExternalServices() {
        when(execution.claimNext()).thenReturn(Optional.empty());
        worker.runOnce();
        verifyNoInteractions(inputs, deadline, provider, generator);
    }

    @Test
    void fallbackModeDoesNotCallRagOrChat() {
        var claim = claim(1, Mode.FALLBACK_ONLY, false);
        worker.runOnce();
        verify(execution).complete(claim, fallback("FALLBACK_ONLY_MODE"));
        verifyNoInteractions(inputs, deadline, provider);
    }

    @Test
    void exhaustedLeaseRecoversWithoutAnotherModelCall() {
        var claim = claim(3, Mode.AI, true);
        worker.runOnce();
        verify(execution).complete(claim, fallback("GENERATION_LEASE_EXHAUSTED"));
        verifyNoInteractions(inputs, deadline, provider);
    }

    @Test
    void emptySearchFallsBackAfterLeaseRenewal() {
        var claim = claim(1, Mode.AI, false);
        when(inputs.load(1L)).thenReturn(input(false));
        when(execution.renew(claim)).thenReturn(true);
        worker.runOnce();
        verify(execution).complete(claim, fallback("RAG_EMPTY"));
        verifyNoInteractions(provider, generator);
    }

    @Test
    void stopsWhenLeaseIsLostBeforeChat() {
        var claim = claim(1, Mode.AI, false);
        when(inputs.load(1L)).thenReturn(input(true));
        when(execution.renew(claim)).thenReturn(false);
        worker.runOnce();
        verifyNoInteractions(provider, generator);
        verify(execution, never()).complete(any(), any());
        verify(execution, never()).fail(any(), anyString(), anyBoolean());
    }

    @Test
    void persistsAiResultAndDoesNotRetryRejectedCompletion() {
        var claim = ai(1);
        Batch batch = new Batch(fallback("TEST").questions(),
                com.interviewai.interview.enums.QuestionGenerationSource.AI, "{}", null);
        when(generator.generate(any(), eq("test-model"))).thenReturn(batch);
        when(execution.complete(claim, batch)).thenReturn(false);
        worker.runOnce();
        verify(execution).complete(claim, batch);
        verify(execution, never()).fail(any(), anyString(), anyBoolean());
    }

    @Test
    void retriesInvalidOutputThenFallsBackAtLimit() {
        var claim = ai(1);
        when(generator.generate(any(), anyString())).thenThrow(invalidOutput());
        worker.runOnce();
        verify(execution).fail(claim, "AI_INVALID_OUTPUT", true);
        var last = claim(3, Mode.AI, false);
        when(execution.renew(last)).thenReturn(true);
        worker.runOnce();
        verify(execution).complete(last, fallback("AI_INVALID_OUTPUT"));
    }

    @ParameterizedTest
    @CsvSource({"429,AI_RATE_LIMITED,true", "503,AI_UNAVAILABLE,true", "401,AI_REQUEST_REJECTED,false"})
    void classifiesProviderStatus(int status, String code, boolean retryable) {
        var claim = ai(1);
        OpenAIServiceException failure = mock(OpenAIServiceException.class);
        when(failure.statusCode()).thenReturn(status);
        if (status == 429) when(failure.code()).thenReturn(Optional.empty());
        when(generator.generate(any(), anyString())).thenThrow(failure);
        worker.runOnce();
        verify(execution).fail(claim, code, retryable);
    }

    @Test
    void quotaExhaustionIsNotRetried() {
        var claim = ai(3);
        OpenAIServiceException failure = mock(OpenAIServiceException.class);
        when(failure.statusCode()).thenReturn(429);
        when(failure.code()).thenReturn(Optional.of("insufficient_quota"));
        when(generator.generate(any(), anyString())).thenThrow(failure);
        worker.runOnce();
        verify(execution).fail(claim, "AI_QUOTA_EXHAUSTED", false);
        verify(execution, never()).complete(any(), any());
    }

    @Test
    void retriesGrpcAndWrappedNetworkFailures() {
        var claim = claim(1, Mode.AI, false);
        when(inputs.load(1L)).thenThrow(Status.UNAVAILABLE.asRuntimeException());
        worker.runOnce();
        verify(execution).fail(claim, "RAG_RPC_FAILED", true);
        doThrow(new RuntimeException(new IOException("network"))).when(inputs).load(1L);
        worker.runOnce();
        verify(execution).fail(claim, "RAG_NETWORK_ERROR", true);
    }

    @Test
    void propagatesDatabaseFailureWithoutFallback() {
        claim(1, Mode.AI, false);
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(inputs.load(1L)).thenThrow(failure);
        assertThatThrownBy(worker::runOnce).isSameAs(failure);
        verify(execution, never()).complete(any(), any());
        verify(execution, never()).fail(any(), anyString(), anyBoolean());
    }

    @Test
    void rejectsUnsupportedVersionWithoutExternalCalls() {
        var claim = new InterviewGenerationExecutionService.Claim(
                1L, UUID.randomUUID().toString(), 1, Mode.AI, "model", "unknown-version", false);
        when(execution.claimNext()).thenReturn(Optional.of(claim));
        worker.runOnce();
        verify(execution).fail(claim, "UNSUPPORTED_GENERATION_VERSION", false);
        verifyNoInteractions(inputs, deadline, provider);
    }

    @Test
    void missingGeneratorIsConfigurationFailure() {
        var claim = claim(1, Mode.AI, false);
        when(inputs.load(1L)).thenReturn(input(true));
        when(execution.renew(claim)).thenReturn(true);
        worker.runOnce();
        verify(execution).fail(claim, "AI_NOT_CONFIGURED", false);
    }

    @Test
    void propagatesCompletionFailureWithoutReclassification() {
        var claim = claim(1, Mode.FALLBACK_ONLY, false);
        var failure = new DataAccessResourceFailureException("commit failed");
        when(execution.complete(eq(claim), any())).thenThrow(failure);
        assertThatThrownBy(worker::runOnce).isSameAs(failure);
        verify(execution, never()).fail(any(), anyString(), anyBoolean());
    }

    @Test
    void preservesInterruptAndLeavesLeaseForRecovery() throws Exception {
        claim(1, Mode.AI, false);
        when(deadline.call(any(), any(), anyString())).thenThrow(new InterruptedException());
        try {
            worker.runOnce();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(execution, never()).complete(any(), any());
            verify(execution, never()).fail(any(), anyString(), anyBoolean());
        } finally {
            // 다음 테스트에 영향을 주지 않도록 현재 스레드의 인터럽트 상태를 초기화한다.
            // noinspection ResultOfMethodCallIgnored
            Thread.interrupted();
        }
    }

    private InterviewGenerationExecutionService.Claim ai(int count) {
        var claim = claim(count, Mode.AI, false);
        when(inputs.load(1L)).thenReturn(input(true));
        when(execution.renew(claim)).thenReturn(true);
        when(provider.getIfAvailable()).thenReturn(generator);
        return claim;
    }

    private InterviewGenerationExecutionService.Claim claim(int count, Mode mode, boolean recovery) {
        var claim = new InterviewGenerationExecutionService.Claim(
                1L, UUID.randomUUID().toString(), count, mode, "test-model", VERSION, recovery);
        when(execution.claimNext()).thenReturn(Optional.of(claim));
        return claim;
    }

    private InterviewGenerationInputService.Input input(boolean context) {
        return new InterviewGenerationInputService.Input("회사", "공고", "직무", "본문", null, null,
                context ? List.of(new RagSearchResult(RagSourceType.COMPANY, 1L, "제목", "본문",
                        0.9, UUID.randomUUID(), 0)) : List.of());
    }
}
