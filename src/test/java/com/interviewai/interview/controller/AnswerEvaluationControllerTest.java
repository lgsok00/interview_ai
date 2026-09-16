package com.interviewai.interview.controller;

import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.global.error.CatalogException;
import com.interviewai.interview.dto.AnswerEvaluationResponse;
import com.interviewai.interview.enums.AnswerEvaluationStatus;
import com.interviewai.interview.service.AnswerEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnswerEvaluationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "auth.jwt.secret=test-jwt-secret-that-is-at-least-32-bytes-long",
        "auth.jwt.access-token-expiration=1h", "auth.jwt.refresh-token-expiration=14d",
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
class AnswerEvaluationControllerTest {

    private static final String ENDPOINT = "/api/interview-sessions/10/answers/30/evaluation";
    private static final LocalDateTime CREATED_AT = LocalDateTime.parse("2026-09-16T10:00:00");
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.parse("2026-09-16T10:00:03");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AnswerEvaluationService evaluations;
    @MockitoBean
    OAuth2AuthenticationSuccessHandler successHandler;
    @MockitoBean
    OAuth2AuthenticationFailureHandler failureHandler;
    @MockitoBean
    GithubOAuth2UserService githubService;

    @Test
    void requestsEvaluationAndReturns202WithoutUnfinishedResultFields() throws Exception {
        when(evaluations.request("1", 10, 30)).thenReturn(pending());

        mvc.perform(post(ENDPOINT).with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(40))
                .andExpect(jsonPath("$.answerId").value(30))
                .andExpect(jsonPath("$.questionId").value(20))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.manualRetryCount").value(0))
                .andExpect(jsonPath("$.starScore").doesNotExist())
                .andExpect(jsonPath("$.failureCode").doesNotExist());

        verify(evaluations).request("1", 10, 30);
    }

    @Test
    void getsCompletedEvaluationWithScoresAndImprovement() throws Exception {
        when(evaluations.get("1", 10, 30)).thenReturn(completed());

        mvc.perform(get(ENDPOINT).with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.starScore").value(4))
                .andExpect(jsonPath("$.logicScore").value(5))
                .andExpect(jsonPath("$.jobFitScore").value(3))
                .andExpect(jsonPath("$.strengths").value("구체적인 근거"))
                .andExpect(jsonPath("$.improvements").value("직무 연결 보강"))
                .andExpect(jsonPath("$.improvedAnswer").value("개선된 답변"))
                .andExpect(jsonPath("$.completedAt").value("2026-09-16T10:00:03"));
    }

    @Test
    void retriesEvaluationAndReturns202() throws Exception {
        var retried = new AnswerEvaluationResponse(
                40L, 30L, 20L, AnswerEvaluationStatus.PENDING,
                null, null, null, null, null, null,
                0, 1, null, CREATED_AT, COMPLETED_AT, null
        );
        when(evaluations.retry("1", 10, 30)).thenReturn(retried);

        mvc.perform(post(ENDPOINT + "/retry").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.manualRetryCount").value(1))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        verify(evaluations).retry("1", 10, 30);
    }

    @Test
    void mapsDisabledNotFoundAndRetryConflictErrors() throws Exception {
        when(evaluations.request(anyString(), anyLong(), anyLong())).thenThrow(
                new CatalogException(HttpStatus.SERVICE_UNAVAILABLE, "ANSWER_EVALUATION_DISABLED", "비활성화"));
        mvc.perform(post(ENDPOINT).with(jwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ANSWER_EVALUATION_DISABLED"));

        when(evaluations.get(anyString(), anyLong(), anyLong())).thenThrow(
                new CatalogException(HttpStatus.NOT_FOUND, "ANSWER_EVALUATION_NOT_FOUND", "없음"));
        mvc.perform(get(ENDPOINT).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANSWER_EVALUATION_NOT_FOUND"));

        when(evaluations.retry(anyString(), anyLong(), anyLong())).thenThrow(
                new CatalogException(HttpStatus.CONFLICT, "ANSWER_EVALUATION_RETRY_CONFLICT", "재시도 불가"));
        mvc.perform(post(ENDPOINT + "/retry").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANSWER_EVALUATION_RETRY_CONFLICT"));
    }

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        mvc.perform(post(ENDPOINT)).andExpect(status().isUnauthorized());
        mvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
        mvc.perform(post(ENDPOINT + "/retry")).andExpect(status().isUnauthorized());

        verifyNoInteractions(evaluations);
    }

    private AnswerEvaluationResponse pending() {
        return new AnswerEvaluationResponse(
                40L, 30L, 20L, AnswerEvaluationStatus.PENDING,
                null, null, null, null, null, null,
                0, 0, null, CREATED_AT, CREATED_AT, null
        );
    }

    private AnswerEvaluationResponse completed() {
        return new AnswerEvaluationResponse(
                40L, 30L, 20L, AnswerEvaluationStatus.COMPLETED,
                4, 5, 3, "구체적인 근거", "직무 연결 보강", "개선된 답변",
                1, 0, null, CREATED_AT, COMPLETED_AT, COMPLETED_AT
        );
    }
}
