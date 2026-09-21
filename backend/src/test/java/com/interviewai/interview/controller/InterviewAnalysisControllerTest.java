package com.interviewai.interview.controller;

import com.interviewai.auth.filter.UserStatusAuthenticationFilter;
import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.global.error.CatalogException;
import com.interviewai.interview.dto.InterviewGrowthAnalysisResponse;
import com.interviewai.interview.dto.InterviewResultResponse;
import com.interviewai.interview.enums.InterviewAnalysisStatus;
import com.interviewai.interview.service.InterviewAnalysisService;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewAnalysisController.class)
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
class InterviewAnalysisControllerTest {
    @MockitoBean
    UserRepository userRepository;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InterviewAnalysisService analysisService;
    @MockitoBean
    OAuth2AuthenticationSuccessHandler successHandler;
    @MockitoBean
    OAuth2AuthenticationFailureHandler failureHandler;
    @MockitoBean
    GithubOAuth2UserService githubService;


    @Test
    void returnsInterviewResult() throws Exception {
        when(analysisService.getResult("1", 10L)).thenReturn(result());

        mvc.perform(get("/api/interview-sessions/10/result")
                        .with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.id").value(10))
                .andExpect(jsonPath("$.analysisStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.overall.averageScore").value(80.0))
                .andExpect(jsonPath("$.questions").isEmpty());

        verify(analysisService).getResult("1", 10L);
    }


    @Test
    void bindsGrowthFiltersAndReturnsAnalysis() throws Exception {
        when(analysisService.getGrowthAnalysis(
                "1",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 16),
                "Backend",
                2L,
                3L
        )).thenReturn(growth());

        mvc.perform(get("/api/interview-growth-analysis")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-16")
                        .param("jobRole", "Backend")
                        .param("companyId", "2")
                        .param("jobPostingId", "3")
                        .with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").value("2026-09-01"))
                .andExpect(jsonPath("$.filters.jobRole").value("Backend"))
                .andExpect(jsonPath("$.summary.sessionCount").value(1))
                .andExpect(jsonPath("$.minimumEvaluatedAnswerCount").value(3));
    }


    @Test
    void mapsResultConflictAndInvalidDateFormat() throws Exception {
        when(analysisService.getResult("1", 10L)).thenThrow(new CatalogException(
                HttpStatus.CONFLICT,
                "INTERVIEW_RESULT_NOT_READY",
                "완료된 면접의 결과만 조회할 수 있습니다."
        ));

        mvc.perform(get("/api/interview-sessions/10/result")
                        .with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTERVIEW_RESULT_NOT_READY"));

        mvc.perform(get("/api/interview-growth-analysis")
                        .param("from", "2026/09/01")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }


    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/interview-sessions/10/result"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/interview-growth-analysis"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(analysisService);
    }


    private InterviewResultResponse result() {
        return new InterviewResultResponse(
                new InterviewResultResponse.Session(
                        10L,
                        "기업",
                        "공고",
                        "Backend",
                        OffsetDateTime.parse("2026-09-16T10:00:00Z")
                ),
                InterviewAnalysisStatus.COMPLETED,
                3,
                3,
                0,
                0,
                0,
                0,
                new InterviewResultResponse.ScoreSummary(
                        3,
                        new BigDecimal("80.0"),
                        new BigDecimal("70.0"),
                        new BigDecimal("80.0"),
                        new BigDecimal("90.0")
                ),
                List.of(),
                List.of()
        );
    }


    private InterviewGrowthAnalysisResponse growth() {
        return new InterviewGrowthAnalysisResponse(
                new InterviewGrowthAnalysisResponse.Period(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 16)
                ),
                new InterviewGrowthAnalysisResponse.Filters("Backend", 2L, 3L),
                new InterviewGrowthAnalysisResponse.Summary(
                        1, 3, 0,
                        new BigDecimal("80.0"),
                        new BigDecimal("70.0"),
                        new BigDecimal("80.0"),
                        new BigDecimal("90.0")
                ),
                List.of(),
                List.of(),
                new InterviewGrowthAnalysisResponse.Change(
                        "RECENT_VS_PREVIOUS", 1, 0,
                        null, null, null, null
                ),
                List.of(),
                List.of(),
                List.of(),
                false,
                3
        );
    }
}
