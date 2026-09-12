package com.interviewai.interview.controller;

import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.interview.dto.CreateInterviewSessionRequest;
import com.interviewai.interview.dto.InterviewSessionResponse;
import com.interviewai.interview.enums.InterviewSessionStatus;
import com.interviewai.interview.exception.RepresentativeResumeNotReadyException;
import com.interviewai.interview.service.InterviewSessionService;
import com.interviewai.global.config.SecurityConfig;
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

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InterviewSessionController.class)
@Import(SecurityConfig.class)
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


    private InterviewSessionResponse response() {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-12T10:00:00Z");

        return new InterviewSessionResponse(
                100L,
                10L,
                20L,
                30L,
                InterviewSessionStatus.GENERATING,
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
                now
        );
    }
}
