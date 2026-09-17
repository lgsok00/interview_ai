package com.interviewai.interview.controller;

import com.interviewai.auth.filter.UserStatusAuthenticationFilter;
import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.global.error.CatalogException;
import com.interviewai.interview.dto.CreateInterviewSessionRequest;
import com.interviewai.interview.dto.InterviewQuestionResponse;
import com.interviewai.interview.dto.InterviewSessionPageResponse;
import com.interviewai.interview.dto.InterviewSessionResponse;
import com.interviewai.interview.dto.InterviewSessionSummaryResponse;
import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.InterviewSessionStatus;
import com.interviewai.interview.enums.QuestionGenerationSource;
import com.interviewai.interview.exception.RepresentativeResumeNotReadyException;
import com.interviewai.interview.service.InterviewSessionService;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InterviewSessionController.class)
@Import({SecurityConfig.class, UserStatusAuthenticationFilter.class})
@TestPropertySource(properties = {
        "auth.jwt.secret=test-jwt-secret-that-is-at-least-32-bytes-long",
        "auth.jwt.access-token-expiration=1h",
        "auth.jwt.refresh-token-expiration=14d",
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret",
        "spring.security.oauth2.client.registration.google.scope[0]=openid",
        "spring.security.oauth2.client.registration.google.scope[1]=profile",
        "spring.security.oauth2.client.registration.google.scope[2]=email",
        "spring.security.oauth2.client.registration.github.client-id=test-github-client-id",
        "spring.security.oauth2.client.registration.github.client-secret=test-github-client-secret",
        "spring.security.oauth2.client.registration.github.scope[0]=read:user",
        "spring.security.oauth2.client.registration.github.scope[1]=user:email"
})
class InterviewSessionControllerTest {
    private static final String SUBJECT = "1";
    @MockitoBean
    UserRepository userRepository;
    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    InterviewSessionService interviewSessionService;
    @MockitoBean
    OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;
    @MockitoBean
    OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;
    @MockitoBean
    GithubOAuth2UserService githubOAuth2UserService;


    @Test
    @DisplayName("JWT 인증 사용자가 면접 세션을 생성하면 스냅샷과 201을 반환한다")
    void createsInterviewSession() throws Exception {
        CreateInterviewSessionRequest request = new CreateInterviewSessionRequest(10L);
        when(interviewSessionService.create(SUBJECT, request)).thenReturn(response());

        mockMvc.perform(post("/api/interview-sessions")
                        .with(jwt().jwt(jwt -> jwt.subject(SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPostingId\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("GENERATING"))
                .andExpect(jsonPath("$.companyName").value("인터뷰AI"))
                .andExpect(jsonPath("$.jobPostingTitle").value("백엔드 개발자"))
                .andExpect(jsonPath("$.coverLetterId").value(20))
                .andExpect(jsonPath("$.resumeId").value(30))
                .andExpect(jsonPath("$.failureCode").doesNotExist());

        verify(interviewSessionService).create(SUBJECT, request);
    }


    @Test
    @DisplayName("채용공고 ID가 누락되면 validation 오류를 반환한다")
    void rejectsMissingJobPostingId() throws Exception {
        mockMvc.perform(post("/api/interview-sessions")
                        .with(jwt().jwt(jwt -> jwt.subject(SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.jobPostingId").value("채용공고 ID는 필수입니다."));

        verifyNoInteractions(interviewSessionService);
    }


    @Test
    @DisplayName("0 이하 채용공고 ID는 validation 오류를 반환한다")
    void rejectsNonPositiveJobPostingId() throws Exception {
        mockMvc.perform(post("/api/interview-sessions")
                        .with(jwt().jwt(jwt -> jwt.subject(SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPostingId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.jobPostingId").value("채용공고 ID는 1 이상이어야 합니다."));

        verifyNoInteractions(interviewSessionService);
    }


    @Test
    @DisplayName("대표 이력서 추출이 완료되지 않았으면 409 오류를 반환한다")
    void returnsConflictWhenRepresentativeResumeIsNotReady() throws Exception {
        CreateInterviewSessionRequest request = new CreateInterviewSessionRequest(10L);
        when(interviewSessionService.create(SUBJECT, request))
                .thenThrow(new RepresentativeResumeNotReadyException());

        mockMvc.perform(post("/api/interview-sessions")
                        .with(jwt().jwt(jwt -> jwt.subject(SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPostingId\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPRESENTATIVE_RESUME_NOT_READY"))
                .andExpect(jsonPath("$.message").value("대표 이력서의 텍스트 추출이 완료되지 않았습니다."));
    }


    @Test
    @DisplayName("JWT가 없으면 면접 세션을 생성할 수 없다")
    void rejectsRequestWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/interview-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPostingId\":10}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(interviewSessionService);
    }


    @Test
    @DisplayName("면접 세션 목록을 페이지 응답으로 조회한다")
    void getsInterviewSessions() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-15T01:00:00Z");
        InterviewSessionSummaryResponse summary = new InterviewSessionSummaryResponse(
                100L, 10L, InterviewSessionStatus.READY,
                "인터뷰AI", "백엔드 개발자", "Backend", null, now, now, null
        );
        when(interviewSessionService.getAll(SUBJECT, 0, 20))
                .thenReturn(new InterviewSessionPageResponse(List.of(summary), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/interview-sessions")
                        .with(jwt().jwt(jwt -> jwt.subject(SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.content[0].status").value("READY"))
                .andExpect(jsonPath("$.content[0].jobPostingContent").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(interviewSessionService).getAll(SUBJECT, 0, 20);
    }


    @Test
    @DisplayName("면접 세션 상세 스냅샷을 조회한다")
    void getsInterviewSession() throws Exception {
        when(interviewSessionService.get(SUBJECT, 100L)).thenReturn(response(InterviewSessionStatus.READY));

        mockMvc.perform(get("/api/interview-sessions/100")
                        .with(jwt().jwt(jwt -> jwt.subject(SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.jobPostingContent").value("채용공고 본문"));
    }


    @Test
    @DisplayName("질문을 순서 및 생성 정보와 함께 제공하되 내부 context는 노출하지 않는다")
    void getsInterviewQuestions() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-15T01:00:00Z");
        when(interviewSessionService.getQuestions(SUBJECT, 100L)).thenReturn(List.of(
                new InterviewQuestionResponse(
                        1L, 1, InterviewQuestionType.TECHNICAL,
                        QuestionGenerationSource.AI, "첫 번째 질문", now
                ),
                new InterviewQuestionResponse(
                        2L, 2, InterviewQuestionType.BEHAVIORAL,
                        QuestionGenerationSource.FALLBACK, "두 번째 질문", now
                )
        ));

        mockMvc.perform(get("/api/interview-sessions/100/questions")
                        .with(jwt().jwt(jwt -> jwt.subject(SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$[0].questionType").value("TECHNICAL"))
                .andExpect(jsonPath("$[0].generationSource").value("AI"))
                .andExpect(jsonPath("$[0].content").value("첫 번째 질문"))
                .andExpect(jsonPath("$[0].contextSnapshot").doesNotExist())
                .andExpect(jsonPath("$[1].sequenceNumber").value(2));
    }


    @Test
    @DisplayName("준비된 면접 세션을 시작한다")
    void startsInterviewSession() throws Exception {
        when(interviewSessionService.start(SUBJECT, 100L))
                .thenReturn(response(InterviewSessionStatus.IN_PROGRESS));

        mockMvc.perform(post("/api/interview-sessions/100/start")
                        .with(jwt().jwt(jwt -> jwt.subject(SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(interviewSessionService).start(SUBJECT, 100L);
    }


    @Test
    @DisplayName("진행 중인 면접 세션을 완료한다")
    void completesInterviewSession() throws Exception {
        when(interviewSessionService.complete(SUBJECT, 100L))
                .thenReturn(response(InterviewSessionStatus.COMPLETED));

        mockMvc.perform(post("/api/interview-sessions/100/complete")
                        .with(jwt().jwt(jwt -> jwt.subject(SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(interviewSessionService).complete(SUBJECT, 100L);
    }


    @Test
    @DisplayName("실패한 질문 생성 작업의 수동 재시도를 접수한다")
    void acceptsManualGenerationRetry() throws Exception {
        mockMvc.perform(post("/api/interview-sessions/100/generation/retry")
                        .with(jwt().jwt(jwt -> jwt.subject(SUBJECT))))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        verify(interviewSessionService).retryGeneration(SUBJECT, 100L);
    }


    @Test
    @DisplayName("잘못된 상태 전이는 409 오류 형식으로 반환한다")
    void returnsConflictForInvalidTransition() throws Exception {
        when(interviewSessionService.start(SUBJECT, 100L)).thenThrow(new CatalogException(
                org.springframework.http.HttpStatus.CONFLICT,
                "INTERVIEW_SESSION_CONFLICT",
                "준비된 면접 세션만 시작할 수 있습니다."
        ));

        mockMvc.perform(post("/api/interview-sessions/100/start")
                        .with(jwt().jwt(jwt -> jwt.subject(SUBJECT))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTERVIEW_SESSION_CONFLICT"))
                .andExpect(jsonPath("$.message").value("준비된 면접 세션만 시작할 수 있습니다."));
    }


    @Test
    @DisplayName("소유하지 않은 면접 세션은 404 오류 형식으로 반환한다")
    void returnsNotFoundForUnownedSession() throws Exception {
        when(interviewSessionService.get(SUBJECT, 100L)).thenThrow(new CatalogException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "INTERVIEW_SESSION_NOT_FOUND",
                "면접 세션을 찾을 수 없습니다."
        ));

        mockMvc.perform(get("/api/interview-sessions/100")
                        .with(jwt().jwt(jwt -> jwt.subject(SUBJECT))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTERVIEW_SESSION_NOT_FOUND"));
    }


    @Test
    @DisplayName("JWT가 없으면 면접 조회와 진행 endpoint를 사용할 수 없다")
    void rejectsProgressRequestsWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/interview-sessions/100"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/interview-sessions/100/start"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/interview-sessions/100/generation/retry"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(interviewSessionService);
    }


    private InterviewSessionResponse response() {
        return response(InterviewSessionStatus.GENERATING);
    }


    private InterviewSessionResponse response(InterviewSessionStatus status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-12T10:00:00Z");

        return new InterviewSessionResponse(
                100L,
                10L,
                20L,
                30L,
                status,
                "인터뷰AI",
                "백엔드 개발자",
                "Backend",
                "채용공고 본문",
                "대표 자기소개서",
                "자기소개서 본문",
                "대표 이력서",
                "이력서 본문",
                null,
                now,
                now,
                status == InterviewSessionStatus.COMPLETED ? now : null
        );
    }
}
