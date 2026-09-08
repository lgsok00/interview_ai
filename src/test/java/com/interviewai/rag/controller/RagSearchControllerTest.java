package com.interviewai.rag.controller;

import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.global.error.CatalogException;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.search.RagSearchResult;
import com.interviewai.rag.service.RagSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RagSearchController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "rag.search.enabled=true",
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
class RagSearchControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RagSearchService searchService;
    @MockitoBean private OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;
    @MockitoBean private OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;
    @MockitoBean private GithubOAuth2UserService githubOAuth2UserService;


    @Test
    @DisplayName("JWT 인증 사용자의 검색 결과를 JSON 배열로 반환한다")
    void searchesDocuments() throws Exception {
        UUID generationId = UUID.randomUUID();
        RagSearchResult result = new RagSearchResult(
                RagSourceType.JOB_POSTING,
                20L,
                "백엔드 개발자",
                "Spring 백엔드 개발자를 채용합니다.",
                0.91,
                generationId,
                1
        );
        when(searchService.search("7", "Spring")).thenReturn(List.of(result));

        mockMvc.perform(get("/api/rag/search")
                        .with(jwt().jwt(token -> token.subject("7")))
                        .param("query", "Spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceType").value("JOB_POSTING"))
                .andExpect(jsonPath("$[0].sourceId").value(20L))
                .andExpect(jsonPath("$[0].title").value("백엔드 개발자"))
                .andExpect(jsonPath("$[0].content").value("Spring 백엔드 개발자를 채용합니다."))
                .andExpect(jsonPath("$[0].score").value(0.91))
                .andExpect(jsonPath("$[0].generationId").value(generationId.toString()))
                .andExpect(jsonPath("$[0].chunkIndex").value(1));

        verify(searchService).search("7", "Spring");
    }


    @Test
    @DisplayName("검색어 validation 오류는 공통 ErrorResponse 형식으로 반환한다")
    void returnsValidationError() throws Exception {
        when(searchService.search("7", " "))
                .thenThrow(CatalogException.invalid("query", "필수 값입니다."));

        mockMvc.perform(get("/api/rag/search")
                        .with(jwt().jwt(token -> token.subject("7")))
                        .param("query", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors.query").value("필수 값입니다."));
    }


    @Test
    @DisplayName("query parameter가 없으면 서비스 호출 전에 400을 반환한다")
    void rejectsMissingQueryParameter() throws Exception {
        mockMvc.perform(get("/api/rag/search")
                        .with(jwt().jwt(token -> token.subject("7"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchService);
    }


    @Test
    @DisplayName("JWT가 없으면 검색 endpoint에 접근할 수 없다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/rag/search").param("query", "Spring"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(searchService);
    }
}
