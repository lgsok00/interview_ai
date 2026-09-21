package com.interviewai.interview.controller;

import com.interviewai.auth.filter.UserStatusAuthenticationFilter;
import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.global.error.CatalogException;
import com.interviewai.interview.dto.InterviewAnswerResponse;
import com.interviewai.interview.dto.InterviewFollowUpResponse;
import com.interviewai.interview.dto.InterviewQuestionResponse;
import com.interviewai.interview.dto.SubmitInterviewAnswerRequest;
import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.QuestionGenerationSource;
import com.interviewai.interview.service.InterviewAnswerService;
import com.interviewai.interview.service.InterviewFollowUpService;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewAnswerController.class)
@Import({SecurityConfig.class, UserStatusAuthenticationFilter.class})
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
class InterviewAnswerControllerTest {
    private static final String BASE = "/api/interview-sessions/10";
    private static final String ANSWER = BASE + "/questions/20/answer";
    private static final String FOLLOW_UP = BASE + "/questions/20/follow-up";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-15T00:00:00Z");
    @MockitoBean
    UserRepository userRepository;
    @Autowired
    MockMvc mvc;

    @MockitoBean
    InterviewAnswerService answers;
    @MockitoBean
    InterviewFollowUpService followUps;
    @MockitoBean
    OAuth2AuthenticationSuccessHandler successHandler;
    @MockitoBean
    OAuth2AuthenticationFailureHandler failureHandler;
    @MockitoBean
    GithubOAuth2UserService githubService;


    @Test
    void submitsNormalizedAnswerAndReturns200() throws Exception {
        when(answers.submit("1", 10, 20, new SubmitInterviewAnswerRequest("답변")))
                .thenReturn(answer());

        mvc.perform(put(ANSWER).with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  답변  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.questionId").value(20))
                .andExpect(jsonPath("$.content").value("답변"));

        verify(answers).submit("1", 10, 20, new SubmitInterviewAnswerRequest("답변"));
    }


    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"content\":null}", "{\"content\":\"\"}", "{\"content\":\"   \"}", "{"})
    void rejectsMissingBlankOrMalformedContent(String body) throws Exception {
        mvc.perform(put(ANSWER)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(answers);
    }


    @Test
    void validatesLengthAfterTrimming() throws Exception {
        String max = "가".repeat(10000);

        mvc.perform(put(ANSWER)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  " + max + "  \"}"))
                .andExpect(status().isOk());

        verify(answers).submit(anyString(), eq(10L), eq(20L), eq(new SubmitInterviewAnswerRequest(max)));
        clearInvocations(answers);

        mvc.perform(put(ANSWER)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + max + "가\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(answers);
    }


    @Test
    void listsAnswerLinksAndGeneratesFollowUpWithoutInternalContext() throws Exception {
        when(answers.getAll("1", 10)).thenReturn(List.of(answer()));
        mvc.perform(
                        get(BASE + "/answers")
                                .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].followUpQuestionId").value(40));

        when(followUps.generate("1", 10, 20))
                .thenReturn(
                        new InterviewFollowUpResponse(
                                20L,
                                new InterviewQuestionResponse(
                                        40L,
                                        6,
                                        InterviewQuestionType.FOLLOW_UP,
                                        QuestionGenerationSource.AI,
                                        "추가 질문",
                                        NOW
                                )
                        )
                );
        mvc.perform(post(FOLLOW_UP).with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentQuestionId").value(20))
                .andExpect(jsonPath("$.question.id").value(40))
                .andExpect(jsonPath("$.question.questionType").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.question.contextSnapshot").doesNotExist());
    }

    @Test
    void mapsConflictNotFoundAndGenerationFailure() throws Exception {
        when(answers.submit(anyString(), anyLong(), anyLong(), any())).thenThrow(
                new CatalogException(HttpStatus.CONFLICT, "INTERVIEW_ANSWER_CONFLICT", "이미 제출됨"));
        mvc.perform(put(ANSWER).with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"답변\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INTERVIEW_ANSWER_CONFLICT"));
        when(answers.getAll(anyString(), anyLong())).thenThrow(
                new CatalogException(HttpStatus.NOT_FOUND, "INTERVIEW_SESSION_NOT_FOUND", "없음"));
        mvc.perform(get(BASE + "/answers").with(jwt())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTERVIEW_SESSION_NOT_FOUND"));
        when(followUps.generate(anyString(), anyLong(), anyLong())).thenThrow(
                new CatalogException(HttpStatus.SERVICE_UNAVAILABLE, "INTERVIEW_FOLLOW_UP_UNAVAILABLE", "생성 실패"));
        mvc.perform(post(FOLLOW_UP).with(jwt())).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INTERVIEW_FOLLOW_UP_UNAVAILABLE"));
    }

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        mvc.perform(put(ANSWER).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"답변\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/answers")).andExpect(status().isUnauthorized());
        mvc.perform(post(FOLLOW_UP)).andExpect(status().isUnauthorized());
        verifyNoInteractions(answers, followUps);
    }

    private InterviewAnswerResponse answer() {
        return new InterviewAnswerResponse(30L, 20L, "답변", 40L, NOW);
    }
}
