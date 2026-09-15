package com.interviewai.interview.service;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.interview.dto.CreateInterviewSessionRequest;
import com.interviewai.interview.dto.InterviewQuestionResponse;
import com.interviewai.interview.dto.InterviewSessionPageResponse;
import com.interviewai.interview.dto.InterviewSessionResponse;
import com.interviewai.interview.entity.InterviewQuestion;
import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.InterviewSessionStatus;
import com.interviewai.interview.enums.QuestionGenerationSource;
import com.interviewai.interview.generation.InterviewGenerationExecutionService;
import com.interviewai.interview.repository.InterviewQuestionRepository;
import com.interviewai.interview.repository.InterviewSessionRepository;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewSessionServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 10, 0);

    @Mock
    InterviewSessionRepository interviewSessionRepository;
    @Mock
    InterviewQuestionRepository interviewQuestionRepository;
    @Mock
    InterviewSessionSnapshotAssembler snapshotAssembler;
    @Mock
    AdminAuthorizationService authorizationService;
    @Mock
    User user;
    @Mock
    InterviewGenerationExecutionService generationExecutionService;

    private InterviewSessionService service;


    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new InterviewSessionService(
                interviewSessionRepository,
                interviewQuestionRepository,
                snapshotAssembler,
                authorizationService,
                generationExecutionService,
                clock
        );
    }


    @Test
    @DisplayName("인증 사용자의 선택 원본 스냅샷으로 생성 중 면접 세션을 저장한다")
    void createsGeneratingSessionFromAssembledSnapshot() {
        CreateInterviewSessionRequest request = new CreateInterviewSessionRequest(10L);
        InterviewSourceSnapshot snapshot = snapshot(true);
        when(authorizationService.requireUser(USER_ID.toString())).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(snapshotAssembler.assemble(USER_ID, 10L)).thenReturn(snapshot);
        when(interviewSessionRepository.save(any(InterviewSession.class))).thenAnswer(invocation -> {
            InterviewSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 100L);
            return session;
        });

        InterviewSessionResponse response = service.create(USER_ID.toString(), request);

        ArgumentCaptor<InterviewSession> captor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository).save(captor.capture());
        InterviewSession saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getStatus()).isEqualTo(InterviewSessionStatus.GENERATING);
        assertThat(saved.getJobPostingId()).isEqualTo(10L);
        assertThat(saved.getCompanyId()).isEqualTo(11L);
        assertThat(saved.getCompanyName()).isEqualTo("인터뷰AI");
        assertThat(saved.getCoverLetterId()).isEqualTo(20L);
        assertThat(saved.getCoverLetterContent()).isEqualTo("자기소개서 본문");
        assertThat(saved.getResumeId()).isEqualTo(30L);
        assertThat(saved.getResumeContent()).isEqualTo("이력서 본문");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(response.id()).isEqualTo(100L);
        verify(generationExecutionService).register(100L);
        assertThat(response.createdAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
    }


    @Test
    @DisplayName("대표 문서가 없으면 개인 문서 필드가 없는 세션을 저장한다")
    void createsSessionWithoutRepresentativeDocuments() {
        when(authorizationService.requireUser(USER_ID.toString())).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(snapshotAssembler.assemble(USER_ID, 10L)).thenReturn(snapshot(false));
        when(interviewSessionRepository.save(any(InterviewSession.class)))
                .thenAnswer(invocation -> {
                    InterviewSession session = invocation.getArgument(0);
                    ReflectionTestUtils.setField(session, "id", 101L);
                    return session;
                });

        InterviewSessionResponse response = service.create(
                USER_ID.toString(), new CreateInterviewSessionRequest(10L)
        );

        assertThat(response.coverLetterId()).isNull();
        assertThat(response.coverLetterTitle()).isNull();
        assertThat(response.coverLetterContent()).isNull();
        assertThat(response.resumeId()).isNull();
        assertThat(response.resumeTitle()).isNull();
        assertThat(response.resumeContent()).isNull();
        verify(generationExecutionService).register(101L);
    }


    @Test
    @DisplayName("스냅샷 조립이 실패하면 면접 세션을 저장하지 않는다")
    void doesNotSaveWhenSnapshotAssemblyFails() {
        RuntimeException failure = new RuntimeException("snapshot failure");
        when(authorizationService.requireUser(USER_ID.toString())).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(snapshotAssembler.assemble(USER_ID, 10L)).thenThrow(failure);

        assertThatThrownBy(() -> service.create(
                USER_ID.toString(), new CreateInterviewSessionRequest(10L)
        )).isSameAs(failure);

        verifyNoInteractions(interviewSessionRepository);
        verifyNoInteractions(generationExecutionService);
    }

    @Test
    void propagatesRegistrationFailure() {
        when(authorizationService.requireUser("1")).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(snapshotAssembler.assemble(USER_ID, 10L)).thenReturn(snapshot(false));
        when(interviewSessionRepository.save(any())).thenAnswer(invocation -> {
            InterviewSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 102L);
            return session;
        });
        RuntimeException failure = new RuntimeException("registration failed");
        doThrow(failure).when(generationExecutionService).register(102L);
        assertThatThrownBy(() -> service.create("1", new CreateInterviewSessionRequest(10L)))
                .isSameAs(failure);
    }

    @Test
    void retriesOnlyForAuthenticatedUser() {
        when(authorizationService.requireUser("1")).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        service.retryGeneration("1", 100L);
        verify(generationExecutionService).retry(USER_ID, 100L);
    }


    @Test
    @DisplayName("인증 사용자의 면접 세션 목록을 최신순 페이지로 조회한다")
    void getsOwnedInterviewSessions() {
        InterviewSession session = session(InterviewSessionStatus.READY);
        PageRequest pageable = PageRequest.of(0, 20,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("createdAt"),
                        org.springframework.data.domain.Sort.Order.desc("id")
                ));
        when(authorizationService.requireUser("1")).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(interviewSessionRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(session), pageable, 1));

        InterviewSessionPageResponse response = service.getAll("1", 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().id()).isEqualTo(100L);
        assertThat(response.content().getFirst().status()).isEqualTo(InterviewSessionStatus.READY);
        assertThat(response.totalElements()).isEqualTo(1);
    }


    @Test
    @DisplayName("소유한 면접 세션의 상세 스냅샷을 조회한다")
    void getsOwnedInterviewSession() {
        InterviewSession session = session(InterviewSessionStatus.READY);
        ownedSession(session);

        InterviewSessionResponse response = service.get("1", 100L);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(InterviewSessionStatus.READY);
        assertThat(response.jobPostingContent()).isEqualTo("채용공고 본문");
    }


    @Test
    @DisplayName("소유하지 않은 면접 세션은 찾을 수 없는 세션으로 처리한다")
    void hidesUnownedInterviewSession() {
        when(authorizationService.requireUser("1")).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(interviewSessionRepository.findByIdAndUser_Id(100L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("1", 100L))
                .isInstanceOf(CatalogException.class)
                .extracting("status", "code")
                .containsExactly(org.springframework.http.HttpStatus.NOT_FOUND, "INTERVIEW_SESSION_NOT_FOUND");
    }


    @Test
    @DisplayName("질문 생성이 끝난 세션의 질문을 순서대로 제공한다")
    void getsQuestionsForReadySession() {
        InterviewSession session = session(InterviewSessionStatus.READY);
        InterviewQuestion second = question(session, 2, "두 번째 질문");
        InterviewQuestion first = question(session, 1, "첫 번째 질문");
        ownedSession(session);
        when(interviewQuestionRepository.findAllBySession_IdOrderBySequenceNumberAsc(100L))
                .thenReturn(List.of(first, second));

        List<InterviewQuestionResponse> response = service.getQuestions("1", 100L);

        assertThat(response).extracting(InterviewQuestionResponse::sequenceNumber)
                .containsExactly(1, 2);
        assertThat(response).extracting(InterviewQuestionResponse::content)
                .containsExactly("첫 번째 질문", "두 번째 질문");
    }


    @Test
    @DisplayName("질문 생성 중이거나 실패한 세션은 질문을 제공하지 않는다")
    void rejectsQuestionsBeforeGenerationCompletes() {
        InterviewSession generating = session(InterviewSessionStatus.GENERATING);
        ownedSession(generating);

        assertThatThrownBy(() -> service.getQuestions("1", 100L))
                .isInstanceOf(CatalogException.class)
                .extracting("status", "code")
                .containsExactly(org.springframework.http.HttpStatus.CONFLICT, "INTERVIEW_SESSION_CONFLICT");

        verifyNoInteractions(interviewQuestionRepository);
    }


    @Test
    @DisplayName("준비된 세션을 잠근 뒤 진행 중 상태로 시작한다")
    void startsReadySession() {
        InterviewSession session = session(InterviewSessionStatus.READY);
        ownedSessionForUpdate(session);

        InterviewSessionResponse response = service.start("1", 100L);

        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
        assertThat(session.getUpdatedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
    }


    @Test
    @DisplayName("준비 상태가 아닌 세션은 시작할 수 없다")
    void rejectsStartingNonReadySession() {
        InterviewSession session = session(InterviewSessionStatus.IN_PROGRESS);
        ownedSessionForUpdate(session);

        assertThatThrownBy(() -> service.start("1", 100L))
                .isInstanceOf(CatalogException.class)
                .extracting("status", "code")
                .containsExactly(org.springframework.http.HttpStatus.CONFLICT, "INTERVIEW_SESSION_CONFLICT");
    }


    @Test
    @DisplayName("진행 중인 세션을 잠근 뒤 완료 상태로 전환한다")
    void completesInProgressSession() {
        InterviewSession session = session(InterviewSessionStatus.IN_PROGRESS);
        ownedSessionForUpdate(session);

        InterviewSessionResponse response = service.complete("1", 100L);

        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.COMPLETED);
        assertThat(session.getUpdatedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(InterviewSessionStatus.COMPLETED);
    }


    @Test
    @DisplayName("진행 중 상태가 아닌 세션은 완료할 수 없다")
    void rejectsCompletingNonInProgressSession() {
        InterviewSession session = session(InterviewSessionStatus.READY);
        ownedSessionForUpdate(session);

        assertThatThrownBy(() -> service.complete("1", 100L))
                .isInstanceOf(CatalogException.class)
                .extracting("status", "code")
                .containsExactly(org.springframework.http.HttpStatus.CONFLICT, "INTERVIEW_SESSION_CONFLICT");
    }


    @Test
    @DisplayName("잘못된 목록 페이지 값은 repository 호출 전에 거부한다")
    void rejectsInvalidPageRequest() {
        when(authorizationService.requireUser("1")).thenReturn(user);

        assertThatThrownBy(() -> service.getAll("1", -1, 20))
                .isInstanceOf(CatalogException.class)
                .extracting("code")
                .isEqualTo("VALIDATION_ERROR");

        verifyNoInteractions(interviewSessionRepository);
    }


    private InterviewSourceSnapshot snapshot(boolean withDocuments) {
        return new InterviewSourceSnapshot(
                new InterviewSourceSnapshot.JobPostingSnapshot(
                        10L, 11L, "인터뷰AI", "백엔드 개발자", "Backend", "채용공고 본문"
                ),
                withDocuments
                        ? new InterviewSourceSnapshot.PersonalDocumentSnapshot(
                        20L, "대표 자기소개서", "자기소개서 본문"
                )
                        : null,
                withDocuments
                        ? new InterviewSourceSnapshot.PersonalDocumentSnapshot(
                        30L, "대표 이력서", "이력서 본문"
                )
                        : null
        );
    }


    private InterviewSession session(InterviewSessionStatus status) {
        InterviewSession session = InterviewSession.create(
                user, 10L, 20L, 30L, 11L,
                "인터뷰AI", "백엔드 개발자", "Backend", "채용공고 본문",
                "대표 자기소개서", "자기소개서 본문", "대표 이력서", "이력서 본문", NOW.minusHours(1)
        );
        ReflectionTestUtils.setField(session, "id", 100L);
        ReflectionTestUtils.setField(session, "status", status);
        return session;
    }


    private InterviewQuestion question(InterviewSession session, int sequence, String content) {
        InterviewQuestion question = InterviewQuestion.create(
                session, sequence, InterviewQuestionType.TECHNICAL,
                QuestionGenerationSource.AI, content, "내부 context", NOW
        );
        ReflectionTestUtils.setField(question, "id", (long) sequence);
        return question;
    }


    private void ownedSession(InterviewSession session) {
        when(authorizationService.requireUser("1")).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(interviewSessionRepository.findByIdAndUser_Id(session.getId(), USER_ID))
                .thenReturn(Optional.of(session));
    }


    private void ownedSessionForUpdate(InterviewSession session) {
        when(authorizationService.requireUser("1")).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(interviewSessionRepository.findOwnedByIdForUpdate(session.getId(), USER_ID))
                .thenReturn(Optional.of(session));
    }
}
