package com.interviewai.coverletter.draft;

import com.interviewai.interview.generation.InterviewGenerationDeadline;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoverLetterDraftWorkerTest {

    private final CoverLetterDraftExecutionService execution = mock(CoverLetterDraftExecutionService.class);
    private final CoverLetterDraftGenerator generator = mock(CoverLetterDraftGenerator.class);
    private final InterviewGenerationDeadline deadline = mock(InterviewGenerationDeadline.class);
    private final CoverLetterDraftWorker worker = new CoverLetterDraftWorker(execution, generator, deadline);
    private final CoverLetterDraftExecutionService.Claim claim = new CoverLetterDraftExecutionService.Claim(
            1L,
            "attempt",
            "test-model",
            CoverLetterDraftPolicy.PROMPT_TEMPLATE_VERSION,
            input()
    );

    @Test
    void recoversExhaustedLeaseBeforeClaimingAndStopsOnEmptyQueue() {
        when(execution.recoverOneExhaustedLease()).thenReturn(true);

        assertThat(worker.runOnce()).isTrue();
        verify(execution, never()).claimNext();

        when(execution.recoverOneExhaustedLease()).thenReturn(false);
        when(execution.claimNext()).thenReturn(Optional.empty());

        assertThat(worker.runOnce()).isFalse();
        verifyNoInteractions(generator, deadline);
    }

    @Test
    void storesSuccessfulGeneratedDraft() throws Exception {
        setupClaim();
        CoverLetterDraft.GenerateDraft generated = new CoverLetterDraft.GenerateDraft(
                "제목", "본문", "요약", List.of()
        );
        when(generator.generate(claim.input(), claim.model(), claim.promptTemplateVersion()))
                .thenReturn(generated);

        assertThat(worker.runOnce()).isTrue();

        verify(execution).complete(claim, generated);
        verify(execution, never()).fail(any(), anyString(), anyBoolean());
    }

    @Test
    void preservesRetryabilityOfInvalidAiOutput() throws Exception {
        setupClaim();
        when(generator.generate(any(), anyString(), anyString()))
                .thenThrow(CoverLetterDraftPolicy.invalidOutput());

        assertThat(worker.runOnce()).isTrue();

        verify(execution).fail(claim, "AI_INVALID_OUTPUT", true);
        verify(execution, never()).complete(any(), any());
    }

    @Test
    void doesNotConvertDatabaseFailureToAiFailure() throws Exception {
        setupClaim();
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(generator.generate(any(), anyString(), anyString())).thenThrow(failure);

        assertThatThrownBy(worker::runOnce).isSameAs(failure);
        verify(execution, never()).fail(any(), anyString(), anyBoolean());
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    void interruptionLeavesLeaseForRecovery() throws Exception {
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

    private void setupClaim() throws Exception {
        when(execution.claimNext()).thenReturn(Optional.of(claim));
        when(deadline.call(any(), any(), anyString()))
                .thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call());
    }

    private CoverLetterDraft.InputSnapshot input() {
        return new CoverLetterDraft.InputSnapshot(
                1L, 2L, 1, "자기소개서", "본문",
                3L, "회사", null, "회사 설명", null, null,
                "공고", "직무", "FULL_TIME", null, "공고 설명", null,
                null, null, "이력서", "이력서 본문", null
        );
    }
}
