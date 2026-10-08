package com.interviewai.interview.service;

import com.interviewai.ai.usage.AiUsageLifecycleService;
import com.interviewai.global.error.CatalogException;
import com.interviewai.interview.dto.InterviewFollowUpResponse;
import com.interviewai.interview.enums.QuestionGenerationSource;
import com.interviewai.interview.generation.InterviewFollowUpGenerator;
import com.interviewai.interview.generation.InterviewGenerationDeadline;
import com.interviewai.interview.generation.InterviewGenerationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class InterviewFollowUpServiceTest {
    private final InterviewAnswerService answers = mock(InterviewAnswerService.class);
    private final InterviewFollowUpGenerator generator = mock(InterviewFollowUpGenerator.class);
    private final InterviewGenerationDeadline deadline = mock(InterviewGenerationDeadline.class);
    private final AiUsageLifecycleService lifecycle = mock(AiUsageLifecycleService.class);
    private final UUID reservation = UUID.randomUUID();
    private final InterviewFollowUpService service = new InterviewFollowUpService(answers, generator, deadline, lifecycle);
    private final InterviewFollowUpGenerator.Input input = new InterviewFollowUpGenerator.Input("직무", "질문", "답변");
    private final InterviewFollowUpGenerator.Generated generated = new InterviewFollowUpGenerator.Generated(
            "꼬리 질문", QuestionGenerationSource.AI, "context");

    @Test
    void returnsExistingWithoutExternalCall() {
        var existing = new InterviewFollowUpResponse(2L, null);
        when(answers.prepareFollowUp("1", 1, 2)).thenReturn(new InterviewAnswerService.Preparation(null, existing, null));
        assertThat(service.generate("1", 1, 2)).isSameAs(existing);
        verifyNoInteractions(deadline, generator, lifecycle);
        verify(answers, never()).saveFollowUp(anyString(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void invokesGeneratorInsideDeadlineThenSaves() throws Exception {
        prepare();
        when(generator.generate(input)).thenReturn(generated);
        when(deadline.call(any(), eq(Duration.ofSeconds(45)), eq("FOLLOW_UP_TIMEOUT")))
                .thenAnswer(invocation -> invocation.<Callable<?>>getArgument(0).call());
        var result = new InterviewFollowUpResponse(2L, null);
        when(answers.saveFollowUp("1", 1, 2, reservation, generated)).thenReturn(result);
        assertThat(service.generate("1", 1, 2)).isSameAs(result);
        var order = inOrder(answers, generator);
        order.verify(answers).prepareFollowUp("1", 1, 2);
        order.verify(generator).generate(input);
        order.verify(answers).saveFollowUp("1", 1, 2, reservation, generated);
        verifyNoInteractions(lifecycle);
    }

    @Test
    void timeoutReturns503WithoutSaving() throws Exception {
        prepare();
        when(deadline.call(any(), any(), anyString()))
                .thenThrow(new InterviewGenerationPolicy.GenerationException("FOLLOW_UP_TIMEOUT", true));
        assertThatThrownBy(() -> service.generate("1", 1, 2)).isInstanceOfSatisfying(CatalogException.class, failure -> {
            assertThat(failure.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(failure.getCode()).isEqualTo("INTERVIEW_FOLLOW_UP_UNAVAILABLE");
        });
        verify(answers, never()).saveFollowUp(anyString(), anyLong(), anyLong(), any(), any());
        verify(lifecycle).cancelFollowUp(reservation);
    }

    @Test
    void restoresInterruptFlag() throws Exception {
        prepare();
        when(deadline.call(any(), any(), anyString())).thenThrow(new InterruptedException());
        doAnswer(invocation -> {
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
            return null;
        }).when(lifecycle).cancelFollowUp(reservation);
        try {
            assertThatThrownBy(() -> service.generate("1", 1, 2)).isInstanceOf(CatalogException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(lifecycle, times(1)).cancelFollowUp(reservation);
        } finally {
            boolean ignored = Thread.interrupted();
        }
    }

    @Test
    void databaseFailureIsNotConvertedToAiFailure() throws Exception {
        prepare();
        when(deadline.call(any(), any(), anyString())).thenReturn(generated);
        var failure = new DataIntegrityViolationException("DB failure");
        when(answers.saveFollowUp("1", 1, 2, reservation, generated)).thenThrow(failure);
        assertThatThrownBy(() -> service.generate("1", 1, 2)).isSameAs(failure);
        verify(lifecycle).cancelFollowUp(reservation);
    }

    @Test
    void ownershipFailurePreventsGeneration() {
        var failure = new CatalogException(HttpStatus.NOT_FOUND, "INTERVIEW_SESSION_NOT_FOUND", "없음");
        when(answers.prepareFollowUp("1", 1, 2)).thenThrow(failure);
        assertThatThrownBy(() -> service.generate("1", 1, 2)).isSameAs(failure);
        verifyNoInteractions(generator, deadline, lifecycle);
    }

    private void prepare() {
        when(answers.prepareFollowUp("1", 1, 2)).thenReturn(new InterviewAnswerService.Preparation(input, null, reservation));
    }

    @Test
    void cleanupFailureDoesNotHideSaveFailure() throws Exception {
        prepare();
        when(deadline.call(any(), any(), anyString())).thenReturn(generated);
        var failure = new CatalogException(HttpStatus.CONFLICT, "AI_REQUEST_EXPIRED", "expired");
        var cleanup = new IllegalStateException("cleanup failed");
        when(answers.saveFollowUp("1", 1, 2, reservation, generated)).thenThrow(failure);
        doThrow(cleanup).when(lifecycle).cancelFollowUp(reservation);
        assertThatThrownBy(() -> service.generate("1", 1, 2)).isSameAs(failure);
        assertThat(failure.getSuppressed()).containsExactly(cleanup);
    }

    @Test
    void generatorFailureCancelsReservationAndReturnsExisting503Contract() throws Exception {
        prepare();
        when(deadline.call(any(), any(), anyString())).thenThrow(new IllegalStateException("model failure"));
        assertThatThrownBy(() -> service.generate("1", 1, 2)).isInstanceOfSatisfying(CatalogException.class,
                failure -> assertThat(failure.getCode()).isEqualTo("INTERVIEW_FOLLOW_UP_UNAVAILABLE"));
        verify(lifecycle).cancelFollowUp(reservation);
    }

    @Test
    void errorAlsoCancelsReservationAndIsRethrown() throws Exception {
        prepare();
        var failure = new AssertionError("generator error");
        when(deadline.call(any(), any(), anyString())).thenThrow(failure);
        assertThatThrownBy(() -> service.generate("1", 1, 2)).isSameAs(failure);
        verify(lifecycle).cancelFollowUp(reservation);
    }
}
