package com.interviewai.interview.generation;

import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.repository.InterviewSessionRepository;
import com.interviewai.interview.service.InterviewRagSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterviewGenerationInputServiceTest {
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<InterviewRagSearchService> provider = mock(ObjectProvider.class);
    private final InterviewGenerationInputService service = new InterviewGenerationInputService(sessions, provider);

    @Test
    void missingSessionFailsBeforeSearch() {
        when(sessions.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.load(1L)).hasMessage("SESSION_NOT_FOUND");
        verifyNoInteractions(provider);
    }

    @Test
    void missingRagBeanUsesStableFailureCode() {
        when(sessions.findById(1L)).thenReturn(Optional.of(mock(InterviewSession.class)));
        assertThatThrownBy(() -> service.load(1L))
                .isInstanceOf(InterviewGenerationPolicy.GenerationException.class)
                .hasMessage("RAG_NOT_CONFIGURED");
    }

    @Test
    void trimsAndBoundsQueryAndPreservesOptionalSnapshots() {
        InterviewSession session = mock(InterviewSession.class);
        InterviewRagSearchService search = mock(InterviewRagSearchService.class);
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        when(provider.getIfAvailable()).thenReturn(search);
        when(session.getJobRole()).thenReturn("  " + "가".repeat(101) + "  ");
        when(session.getJobPostingContent()).thenReturn("생성 당시 본문");
        when(search.search(session, "가".repeat(100))).thenReturn(List.of());
        var input = service.load(1L);
        assertThat(input.jobPostingContent()).isEqualTo("생성 당시 본문");
        assertThat(input.coverLetterContent()).isNull();
        assertThat(input.resumeContent()).isNull();
        assertThat(input.context()).isEmpty();
        verify(search).search(session, "가".repeat(100));
    }
}
