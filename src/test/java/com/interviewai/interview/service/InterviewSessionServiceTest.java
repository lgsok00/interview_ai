package com.interviewai.interview.service;

import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.interview.dto.CreateInterviewSessionRequest;
import com.interviewai.interview.dto.InterviewSessionResponse;
import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.enums.InterviewSessionStatus;
import com.interviewai.interview.repository.InterviewSessionRepository;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
    InterviewSessionSnapshotAssembler snapshotAssembler;
    @Mock
    AdminAuthorizationService authorizationService;
    @Mock
    User user;

    private InterviewSessionService service;


    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new InterviewSessionService(
                interviewSessionRepository,
                snapshotAssembler,
                authorizationService,
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
        assertThat(saved.getCompanyName()).isEqualTo("인터뷰AI");
        assertThat(saved.getCoverLetterId()).isEqualTo(20L);
        assertThat(saved.getCoverLetterContent()).isEqualTo("자기소개서 본문");
        assertThat(saved.getResumeId()).isEqualTo(30L);
        assertThat(saved.getResumeContent()).isEqualTo("이력서 본문");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.createdAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
    }


    @Test
    @DisplayName("대표 문서가 없으면 개인 문서 필드가 없는 세션을 저장한다")
    void createsSessionWithoutRepresentativeDocuments() {
        when(authorizationService.requireUser(USER_ID.toString())).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(snapshotAssembler.assemble(USER_ID, 10L)).thenReturn(snapshot(false));
        when(interviewSessionRepository.save(any(InterviewSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InterviewSessionResponse response = service.create(
                USER_ID.toString(), new CreateInterviewSessionRequest(10L)
        );

        assertThat(response.coverLetterId()).isNull();
        assertThat(response.coverLetterTitle()).isNull();
        assertThat(response.coverLetterContent()).isNull();
        assertThat(response.resumeId()).isNull();
        assertThat(response.resumeTitle()).isNull();
        assertThat(response.resumeContent()).isNull();
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
    }


    private InterviewSourceSnapshot snapshot(boolean withDocuments) {
        return new InterviewSourceSnapshot(
                new InterviewSourceSnapshot.JobPostingSnapshot(
                        10L, "인터뷰AI", "백엔드 개발자", "Backend", "채용공고 본문"
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
}
