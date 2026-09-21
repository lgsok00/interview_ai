package com.interviewai.interview.service;

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
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class InterviewFollowUpServiceTest {
    private final InterviewAnswerService answers = mock(InterviewAnswerService.class);
    private final InterviewFollowUpGenerator generator = mock(InterviewFollowUpGenerator.class);
    private final InterviewGenerationDeadline deadline = mock(InterviewGenerationDeadline.class);
    private final InterviewFollowUpService service = new InterviewFollowUpService(answers, generator, deadline);
    private final InterviewFollowUpGenerator.Input input = new InterviewFollowUpGenerator.Input("직무", "질문", "답변");
    private final InterviewFollowUpGenerator.Generated generated = new InterviewFollowUpGenerator.Generated(
            "꼬리 질문", QuestionGenerationSource.AI, "context");

    @Test
    void returnsExistingWithoutExternalCall() {
        var existing = new InterviewFollowUpResponse(2L, null);
        when(answers.prepareFollowUp("1", 1, 2)).thenReturn(new InterviewAnswerService.Preparation(null, existing));
        assertThat(service.generate("1", 1, 2)).isSameAs(existing);
        verifyNoInteractions(deadline, generator);
        verify(answers, never()).saveFollowUp(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    void invokesGeneratorInsideDeadlineThenSaves() throws Exception {
        prepare();
        when(generator.generate(input)).thenReturn(generated);
        when(deadline.call(any(), eq(Duration.ofSeconds(45)), eq("FOLLOW_UP_TIMEOUT")))
                .thenAnswer(invocation -> invocation.<Callable<?>>getArgument(0).call());
        var result = new InterviewFollowUpResponse(2L, null);
        when(answers.saveFollowUp("1", 1, 2, generated)).thenReturn(result);
        assertThat(service.generate("1", 1, 2)).isSameAs(result);
        var order = inOrder(answers, generator);
        order.verify(answers).prepareFollowUp("1", 1, 2);
        order.verify(generator).generate(input);
        order.verify(answers).saveFollowUp("1", 1, 2, generated);
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
        verify(answers, never()).saveFollowUp(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    void restoresInterruptFlag() throws Exception {
        prepare();
        when(deadline.call(any(), any(), anyString())).thenThrow(new InterruptedException());
        try {
            assertThatThrownBy(() -> service.generate("1", 1, 2)).isInstanceOf(CatalogException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            boolean ignored = Thread.interrupted();
        }
    }

    @Test
    void databaseFailureIsNotConvertedToAiFailure() throws Exception {
        prepare();
        when(deadline.call(any(), any(), anyString())).thenReturn(generated);
        var failure = new DataIntegrityViolationException("DB failure");
        when(answers.saveFollowUp("1", 1, 2, generated)).thenThrow(failure);
        assertThatThrownBy(() -> service.generate("1", 1, 2)).isSameAs(failure);
    }

    @Test
    void ownershipFailurePreventsGeneration() {
        var failure = new CatalogException(HttpStatus.NOT_FOUND, "INTERVIEW_SESSION_NOT_FOUND", "없음");
        when(answers.prepareFollowUp("1", 1, 2)).thenThrow(failure);
        assertThatThrownBy(() -> service.generate("1", 1, 2)).isSameAs(failure);
        verifyNoInteractions(generator, deadline);
    }

    private void prepare() {
        when(answers.prepareFollowUp("1", 1, 2)).thenReturn(new InterviewAnswerService.Preparation(input, null));
    }
}
