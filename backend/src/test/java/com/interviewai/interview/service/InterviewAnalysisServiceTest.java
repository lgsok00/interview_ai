package com.interviewai.interview.service;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.interview.dto.InterviewGrowthAnalysisResponse;
import com.interviewai.interview.dto.InterviewResultResponse;
import com.interviewai.interview.enums.AnswerEvaluationStatus;
import com.interviewai.interview.enums.InterviewAnalysisStatus;
import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.repository.InterviewAnalysisQueryRepository;
import com.interviewai.interview.repository.InterviewAnalysisQueryRepository.GrowthRow;
import com.interviewai.interview.repository.InterviewAnalysisQueryRepository.ResultRow;
import com.interviewai.interview.repository.InterviewAnalysisQueryRepository.SessionView;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewAnalysisServiceTest {

    private static final long USER_ID = 1L;
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 9, 15, 10, 0);

    @Mock
    InterviewAnalysisQueryRepository repository;
    @Mock
    AdminAuthorizationService authorization;
    @Mock
    User user;

    private InterviewAnalysisService service;


    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);
        service = new InterviewAnalysisService(repository, authorization, clock);
    }


    @Test
    void returnsPartialResultAndExcludesUnfinishedEvaluationsFromScores() {
        authenticate();
        when(repository.findOwnedSession(USER_ID, 10L)).thenReturn(Optional.of(completedSession()));
        when(repository.findResultRows(10L)).thenReturn(List.of(
                completedRow(),
                row(2L, InterviewQuestionType.TECHNICAL, AnswerEvaluationStatus.PENDING),
                row(3L, InterviewQuestionType.BEHAVIORAL, AnswerEvaluationStatus.FAILED),
                row(4L, InterviewQuestionType.FOLLOW_UP, null)
        ));

        InterviewResultResponse response = service.getResult("1", 10L);

        assertThat(response.analysisStatus()).isEqualTo(InterviewAnalysisStatus.PARTIAL);
        assertThat(response.answerCount()).isEqualTo(4);
        assertThat(response.completedEvaluationCount()).isEqualTo(1);
        assertThat(response.pendingEvaluationCount()).isEqualTo(1);
        assertThat(response.failedEvaluationCount()).isEqualTo(1);
        assertThat(response.notRequestedEvaluationCount()).isEqualTo(1);
        assertThat(response.overall().averageScore()).isEqualByComparingTo("70.0");
        assertThat(response.overall().starScore()).isEqualByComparingTo("80.0");
        assertThat(response.questions()).hasSize(4);
        assertThat(response.questions().getFirst().evaluation().averageScore())
                .isEqualByComparingTo("70.0");
        assertThat(response.questions().get(1).evaluation().averageScore()).isNull();
        assertThat(response.questions().get(3).evaluation()).isNull();
    }


    @Test
    void returnsPendingResultWhenNoEvaluationHasCompleted() {
        authenticate();
        when(repository.findOwnedSession(USER_ID, 10L)).thenReturn(Optional.of(completedSession()));
        when(repository.findResultRows(10L)).thenReturn(List.of(
                row(1L, InterviewQuestionType.TECHNICAL, AnswerEvaluationStatus.FAILED),
                row(2L, InterviewQuestionType.BEHAVIORAL, null)
        ));

        InterviewResultResponse response = service.getResult("1", 10L);

        assertThat(response.analysisStatus()).isEqualTo(InterviewAnalysisStatus.PENDING);
        assertThat(response.overall().sampleCount()).isZero();
        assertThat(response.overall().averageScore()).isNull();
    }


    @Test
    void hidesUnownedSessionAndRejectsUnfinishedSession() {
        authenticate();
        when(repository.findOwnedSession(USER_ID, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResult("1", 10L))
                .isInstanceOf(CatalogException.class)
                .extracting("status", "code")
                .containsExactly(org.springframework.http.HttpStatus.NOT_FOUND, "INTERVIEW_SESSION_NOT_FOUND");
        verifyNoInteractionsAfterOwnedLookup();

        when(repository.findOwnedSession(USER_ID, 11L)).thenReturn(Optional.of(
                new SessionView(11L, "IN_PROGRESS", 2L, "기업", 3L, "공고", "Backend", null)
        ));

        assertThatThrownBy(() -> service.getResult("1", 11L))
                .isInstanceOf(CatalogException.class)
                .extracting("status", "code")
                .containsExactly(org.springframework.http.HttpStatus.CONFLICT, "INTERVIEW_RESULT_NOT_READY");
    }


    @Test
    void aggregatesGrowthAndBuildsDeterministicRoadmap() {
        authenticate();
        List<GrowthRow> rows = List.of(
                growth(1L, 1, 50, 70, 80),
                growth(1L, 1, 60, 80, 90),
                growth(2L, 2, 70, 90, 100),
                growth(2L, 2, 80, 100, 90)
        );
        when(repository.findGrowthRows(
                USER_ID,
                LocalDateTime.of(2026, 6, 18, 0, 0),
                LocalDateTime.of(2026, 9, 17, 0, 0),
                "Backend", 2L, 3L
        )).thenReturn(rows);
        when(repository.countExcludedCompletedSessions(
                USER_ID,
                LocalDateTime.of(2026, 6, 18, 0, 0),
                LocalDateTime.of(2026, 9, 17, 0, 0),
                "Backend", 2L, 3L
        )).thenReturn(1);

        InterviewGrowthAnalysisResponse response = service.getGrowthAnalysis(
                "1", null, null, "  Backend  ", 2L, 3L
        );

        assertThat(response.period().from()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(response.period().to()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(response.summary().sessionCount()).isEqualTo(2);
        assertThat(response.summary().evaluatedAnswerCount()).isEqualTo(4);
        assertThat(response.summary().excludedSessionCount()).isEqualTo(1);
        assertThat(response.summary().starScore()).isEqualByComparingTo("65.0");
        assertThat(response.summary().averageScore()).isEqualByComparingTo("80.0");
        assertThat(response.change().recentSessionCount()).isEqualTo(1);
        assertThat(response.change().previousSessionCount()).isEqualTo(1);
        assertThat(response.change().starScore()).isEqualByComparingTo("20.0");
        assertThat(response.insufficientData()).isFalse();
        assertThat(response.learningRoadmap()).isNotEmpty();
        assertThat(response.learningRoadmap().getFirst().dimension()).isEqualTo("STAR");
        assertThat(response.learningRoadmap().getFirst().targetScore()).isEqualByComparingTo("75.0");
    }


    @Test
    void returnsEmptyGrowthAnalysisWhenThereAreNoCompletedEvaluations() {
        authenticate();
        when(repository.findGrowthRows(
                USER_ID,
                LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 9, 17, 0, 0),
                null, null, null
        )).thenReturn(List.of());
        when(repository.countExcludedCompletedSessions(
                USER_ID,
                LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 9, 17, 0, 0),
                null, null, null
        )).thenReturn(2);

        InterviewGrowthAnalysisResponse response = service.getGrowthAnalysis(
                "1", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 16), null, null, null
        );

        assertThat(response.summary().sessionCount()).isZero();
        assertThat(response.summary().excludedSessionCount()).isEqualTo(2);
        assertThat(response.summary().averageScore()).isNull();
        assertThat(response.trends()).isEmpty();
        assertThat(response.strengths()).isEmpty();
        assertThat(response.weaknesses()).isEmpty();
        assertThat(response.learningRoadmap()).isEmpty();
        assertThat(response.insufficientData()).isTrue();
    }


    @Test
    void validatesGrowthRangeAndIdentifiersBeforeQueryingAnalysisData() {
        authenticate();

        assertThatThrownBy(() -> service.getGrowthAnalysis(
                "1", LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 16), null, null, null
        )).isInstanceOf(CatalogException.class).extracting("code").isEqualTo("VALIDATION_ERROR");

        assertThatThrownBy(() -> service.getGrowthAnalysis(
                "1", LocalDate.of(2025, 9, 15), LocalDate.of(2026, 9, 16), null, null, null
        )).isInstanceOf(CatalogException.class).extracting("code").isEqualTo("VALIDATION_ERROR");

        assertThatThrownBy(() -> service.getGrowthAnalysis(
                "1", null, null, null, 0L, null
        )).isInstanceOf(CatalogException.class).extracting("code").isEqualTo("VALIDATION_ERROR");

        verifyNoInteractions(repository);
    }


    private void authenticate() {
        when(authorization.requireUser("1")).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
    }


    private SessionView completedSession() {
        return new SessionView(10L, "COMPLETED", 2L, "기업", 3L, "공고", "Backend", COMPLETED_AT);
    }


    private ResultRow completedRow() {
        return new ResultRow(
                1L, null, InterviewQuestionType.TECHNICAL, (int) 1L, "질문 " + 1L, 100L + 1L, "답변 " + 1L,
                AnswerEvaluationStatus.COMPLETED, 80, 70, 60,
                "강점", "개선", "개선 답변", null, COMPLETED_AT
        );
    }


    private ResultRow row(long id, InterviewQuestionType type, AnswerEvaluationStatus status) {
        return new ResultRow(
                id, null, type, (int) id, "질문 " + id, 100L + id, "답변 " + id,
                status, null, null, null, null, null, null,
                status == AnswerEvaluationStatus.FAILED ? "FAILED" : null, null
        );
    }


    private GrowthRow growth(long sessionId, int day, int star, int logic, int jobFit) {
        return new GrowthRow(
                sessionId,
                LocalDateTime.of(2026, 9, day, 10, 0),
                "기업 " + sessionId,
                "Backend",
                InterviewQuestionType.TECHNICAL,
                star,
                logic,
                jobFit
        );
    }


    private void verifyNoInteractionsAfterOwnedLookup() {
        verify(repository).findOwnedSession(USER_ID, 10L);
    }
}
