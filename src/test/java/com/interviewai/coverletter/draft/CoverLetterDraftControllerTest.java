package com.interviewai.coverletter.draft;

import com.interviewai.auth.filter.UserStatusAuthenticationFilter;
import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.coverletter.dto.CoverLetterResponse;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoverLetterDraftController.class)
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
class CoverLetterDraftControllerTest {

    private static final String BASE = "/api/cover-letters/10/drafts";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 10, 0);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CoverLetterDraftService draftService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    OAuth2AuthenticationSuccessHandler successHandler;

    @MockitoBean
    OAuth2AuthenticationFailureHandler failureHandler;

    @MockitoBean
    GithubOAuth2UserService githubService;

    @Test
    void createsDraftAndReturns202() throws Exception {
        CreateCoverLetterDraftRequest request = new CreateCoverLetterDraftRequest(20L, 30L, "강조");
        when(draftService.create("1", 10L, request)).thenReturn(pending());

        mvc.perform(post(BASE)
                        .with(jwt().jwt(token -> token.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobPostingId":20,"resumeId":30,"instruction":" 강조 "}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(40))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.baseVersionNumber").value(1));

        verify(draftService).create("1", 10L, request);
    }

    @Test
    void rejectsBlankInstructionAndInvalidIds() throws Exception {
        mvc.perform(post(BASE)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobPostingId":0,"resumeId":-1,"instruction":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.jobPostingId").exists())
                .andExpect(jsonPath("$.errors.resumeId").exists())
                .andExpect(jsonPath("$.errors.instruction").exists());

        verifyNoInteractions(draftService);
    }

    @Test
    void returnsListAndDetailWithoutPromptOrInternalReasoning() throws Exception {
        when(draftService.getAll("1", 10L)).thenReturn(List.of(summary()));
        when(draftService.get("1", 10L, 40L)).thenReturn(ready());

        mvc.perform(get(BASE).with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(40))
                .andExpect(jsonPath("$[0].generatedTitle").value("AI 제목"));

        mvc.perform(get(BASE + "/40").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedContent").value("AI 본문"))
                .andExpect(jsonPath("$.warnings[0]").value("확인 필요"))
                .andExpect(jsonPath("$.prompt").doesNotExist())
                .andExpect(jsonPath("$.reasoning").doesNotExist());
    }

    @Test
    void regeneratesDraftAndReturns202() throws Exception {
        CoverLetterDraftResponse regenerated = new CoverLetterDraftResponse(
                41L, 10L, 40L, 20L, 30L, 2, "b".repeat(64),
                CoverLetterDraftStatus.PENDING, "강조", null, null, null,
                List.of(), null, 0, null, NOW, NOW, null, null
        );
        when(draftService.regenerate("1", 10L, 40L)).thenReturn(regenerated);

        mvc.perform(post(BASE + "/40/regenerate")
                        .with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.sourceDraftId").value(40));
    }

    @Test
    void appliesReviewedContent() throws Exception {
        ApplyCoverLetterDraftRequest request = new ApplyCoverLetterDraftRequest("검수 제목", "검수 본문");
        when(draftService.apply("1", 10L, 40L, request)).thenReturn(new CoverLetterResponse(
                10L, "검수 제목", "검수 본문", 2, false, NOW, NOW
        ));

        mvc.perform(post(BASE + "/40/apply")
                        .with(jwt().jwt(token -> token.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" 검수 제목 ","content":" 검수 본문 "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersionNumber").value(2))
                .andExpect(jsonPath("$.title").value("검수 제목"));

        verify(draftService).apply("1", 10L, 40L, request);
    }

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        mvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPostingId\":20}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/40")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/40/regenerate")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/40/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"본문\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(draftService);
    }

    private CoverLetterDraftResponse pending() {
        return new CoverLetterDraftResponse(
                40L, 10L, null, 20L, 30L, 1, "a".repeat(64),
                CoverLetterDraftStatus.PENDING, "강조", null, null, null,
                List.of(), null, 0, null, NOW, NOW, null, null
        );
    }

    private CoverLetterDraftResponse ready() {
        return new CoverLetterDraftResponse(
                40L, 10L, null, 20L, 30L, 1, "a".repeat(64),
                CoverLetterDraftStatus.REVIEW_READY, "강조", "AI 제목", "AI 본문", "요약",
                List.of("확인 필요"), null, 1, null, NOW, NOW, NOW, null
        );
    }

    private CoverLetterDraftSummaryResponse summary() {
        return new CoverLetterDraftSummaryResponse(
                40L, null, 20L, 30L, 1, CoverLetterDraftStatus.REVIEW_READY,
                "AI 제목", "요약", List.of("확인 필요"), null, null,
                NOW, NOW, NOW, null
        );
    }
}
