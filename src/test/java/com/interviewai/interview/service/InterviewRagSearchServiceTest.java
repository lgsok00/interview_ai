package com.interviewai.interview.service;

import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.search.RagSearchResult;
import com.interviewai.rag.search.RagSearchScope;
import com.interviewai.rag.service.RagSearchService;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewRagSearchServiceTest {

    @Mock
    private RagSearchService ragSearchService;

    private InterviewRagSearchService service;

    @BeforeEach
    void setUp() {
        service = new InterviewRagSearchService(ragSearchService);
    }

    @Test
    @DisplayName("세션에 저장된 기업 공고 자기소개서 이력서만 내부 검색 범위로 전달한다")
    void searchesWithinAllSessionSources() {
        InterviewSession session = session(30L, 40L);
        when(ragSearchService.searchWithin(any(RagSearchScope.class), eq("질문")))
                .thenReturn(List.of());

        assertThat(service.search(session, "질문")).isEmpty();

        ArgumentCaptor<RagSearchScope> captor = ArgumentCaptor.forClass(RagSearchScope.class);
        verify(ragSearchService).searchWithin(captor.capture(), eq("질문"));
        assertThat(captor.getValue().user()).isSameAs(session.getUser());
        assertThat(captor.getValue().allowedSourceKeys()).containsExactlyInAnyOrder(
                new RagSourceKey(RagSourceType.COMPANY, 10L),
                new RagSourceKey(RagSourceType.JOB_POSTING, 20L),
                new RagSourceKey(RagSourceType.COVER_LETTER, 30L),
                new RagSourceKey(RagSourceType.RESUME, 40L)
        );
    }

    @Test
    @DisplayName("선택 개인 문서가 없으면 기업과 공고만 검색 범위에 포함한다")
    void omitsMissingOptionalDocuments() {
        InterviewSession session = session(null, null);
        when(ragSearchService.searchWithin(any(RagSearchScope.class), anyString()))
                .thenReturn(List.of());

        service.search(session, "질문");

        ArgumentCaptor<RagSearchScope> captor = ArgumentCaptor.forClass(RagSearchScope.class);
        verify(ragSearchService).searchWithin(captor.capture(), eq("질문"));
        assertThat(captor.getValue().allowedSourceKeys()).containsExactlyInAnyOrder(
                new RagSourceKey(RagSourceType.COMPANY, 10L),
                new RagSourceKey(RagSourceType.JOB_POSTING, 20L)
        );
    }

    @Test
    @DisplayName("RAG 검색 결과와 장애를 변경하지 않고 호출자에게 전달한다")
    void propagatesResultAndFailure() {
        InterviewSession session = session(null, null);
        List<RagSearchResult> results = List.of();
        when(ragSearchService.searchWithin(any(RagSearchScope.class), eq("정상"))).thenReturn(results);
        RuntimeException failure = new RuntimeException("vector store failure");
        when(ragSearchService.searchWithin(any(RagSearchScope.class), eq("실패"))).thenThrow(failure);

        assertThat(service.search(session, "정상")).isSameAs(results);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.search(session, "실패"))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("세션이 없으면 RAG 서비스를 호출하지 않는다")
    void rejectsMissingSession() {
        assertThatNullPointerException().isThrownBy(() -> service.search(null, "질문"));

        verifyNoInteractions(ragSearchService);
    }

    private InterviewSession session(Long coverLetterId, Long resumeId) {
        User user = User.createLocalUser("user@example.com", "{bcrypt}encoded", "사용자");
        ReflectionTestUtils.setField(user, "id", 7L);
        return InterviewSession.create(
                user,
                20L,
                coverLetterId,
                resumeId,
                10L,
                "인터뷰AI",
                "백엔드 개발자",
                "Backend",
                "채용공고 본문",
                coverLetterId == null ? null : "대표 자기소개서",
                coverLetterId == null ? null : "자기소개서 본문",
                resumeId == null ? null : "대표 이력서",
                resumeId == null ? null : "이력서 본문",
                LocalDateTime.of(2026, 9, 14, 10, 0)
        );
    }
}
