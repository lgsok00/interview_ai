package com.interviewai.rag.controller;

import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.global.error.CatalogException;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.dto.AdminRagResponse;
import com.interviewai.rag.index.RagIndexJobStatus;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.service.AdminRagReindexService;
import com.interviewai.rag.service.AdminRagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRagController.class)
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
class AdminRagControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    AdminRagService service;
    @MockitoBean
    AdminRagReindexService reindex;
    @MockitoBean
    OAuth2AuthenticationSuccessHandler successHandler;
    @MockitoBean
    OAuth2AuthenticationFailureHandler failureHandler;
    @MockitoBean
    GithubOAuth2UserService githubService;

    @Test
    void bindsDefaultPagesAndReturnsSafeMetadata() throws Exception {
        when(service.sources("1", null, null, 0, 20))
                .thenReturn(AdminRagResponse.Page.of(List.of(source()), 0, 20, 1));
        when(service.jobs("1", null, null, null, null, 0, 20))
                .thenReturn(AdminRagResponse.Page.of(List.of(job()), 0, 20, 1));
        var sources = mvc.perform(get("/api/admin/rag/sources").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].tombstoneSequence").value(0));
        assertNoPayload(sources, "$.items[0]");
        var jobs = mvc.perform(get("/api/admin/rag/jobs").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-09-17T00:00:00Z"));
        assertNoPayload(jobs, "$.items[0]");
    }

    @Test
    void bindsAllFiltersAndPagination() throws Exception {
        when(service.jobs("1", RagSourceType.RESUME, 9L, RagIndexJobStatus.FAILED, RagIndexOperation.UPSERT, 2, 5))
                .thenReturn(AdminRagResponse.Page.of(List.of(), 2, 5, 0));
        when(service.sources("1", RagSourceType.RESUME, 9L, 2, 5))
                .thenReturn(AdminRagResponse.Page.of(List.of(), 2, 5, 0));
        mvc.perform(get("/api/admin/rag/jobs").with(jwt().jwt(token -> token.subject("1")))
                        .param("sourceType", "RESUME").param("sourceId", "9")
                        .param("status", "FAILED").param("operation", "UPSERT")
                        .param("page", "2").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/rag/sources").with(jwt().jwt(token -> token.subject("1")))
                        .param("sourceType", "RESUME").param("sourceId", "9")
                        .param("page", "2").param("size", "5"))
                .andExpect(status().isOk());
        verify(service).jobs("1", RagSourceType.RESUME, 9L, RagIndexJobStatus.FAILED, RagIndexOperation.UPSERT, 2, 5);
        verify(service).sources("1", RagSourceType.RESUME, 9L, 2, 5);
    }

    @Test
    void detailAndBothAcceptedCommandsNeverExposePayload() throws Exception {
        when(service.source("1", RagSourceType.RESUME, 9L)).thenReturn(source());
        when(service.job("1", 10L)).thenReturn(job());
        when(service.retry("1", 10L)).thenReturn(job());
        when(reindex.reindex("1", RagSourceType.RESUME, 9L)).thenReturn(job());
        assertNoPayload(mvc.perform(get("/api/admin/rag/sources/RESUME/9")
                .with(jwt().jwt(token -> token.subject("1")))).andExpect(status().isOk()), "$");
        assertNoPayload(mvc.perform(get("/api/admin/rag/jobs/10")
                .with(jwt().jwt(token -> token.subject("1")))).andExpect(status().isOk()), "$");
        assertNoPayload(mvc.perform(post("/api/admin/rag/jobs/10/retry")
                        .with(jwt().jwt(token -> token.subject("1")))).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(10)), "$");
        assertNoPayload(mvc.perform(post("/api/admin/rag/sources/RESUME/9/reindex")
                .with(jwt().jwt(token -> token.subject("1")))).andExpect(status().isAccepted()), "$");
        verify(service).retry("1", 10L);
        verify(reindex).reindex("1", RagSourceType.RESUME, 9L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sourceType", "sourceId", "status", "operation", "page", "size"})
    void rejectsInvalidQueryTypes(String parameter) throws Exception {
        mvc.perform(get("/api/admin/rag/jobs").param(parameter, "invalid").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service, reindex);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/rag/jobs/abc", "/api/admin/rag/sources/INVALID/9",
            "/api/admin/rag/sources/RESUME/abc"})
    void rejectsInvalidPathTypes(String path) throws Exception {
        mvc.perform(get(path).with(jwt())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service, reindex);
    }

    @ParameterizedTest
    @CsvSource({"403,FORBIDDEN", "404,RAG_JOB_NOT_FOUND", "409,RAG_JOB_NOT_FAILED",
            "409,RAG_JOB_RETRY_LIMIT_EXCEEDED", "409,RAG_JOB_SUPERSEDED", "409,RAG_JOB_RETRY_CONFLICT"})
    void mapsRetryErrors(int statusCode, String code) throws Exception {
        when(service.retry("1", 10L)).thenThrow(new CatalogException(HttpStatus.valueOf(statusCode), code, "실패"));
        mvc.perform(post("/api/admin/rag/jobs/10/retry").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().is(statusCode)).andExpect(jsonPath("$.code").value(code));
    }

    @ParameterizedTest
    @CsvSource({"404,RAG_SOURCE_NOT_FOUND", "409,RAG_SOURCE_NOT_READY", "403,FORBIDDEN"})
    void mapsReindexErrors(int statusCode, String code) throws Exception {
        when(reindex.reindex("1", RagSourceType.RESUME, 9L))
                .thenThrow(new CatalogException(HttpStatus.valueOf(statusCode), code, "실패"));
        mvc.perform(post("/api/admin/rag/sources/RESUME/9/reindex").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().is(statusCode)).andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void requiresAuthenticationForAllSixEndpoints() throws Exception {
        for (String path : List.of("/sources", "/sources/RESUME/9", "/jobs", "/jobs/10")) {
            mvc.perform(get("/api/admin/rag" + path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/admin/rag/jobs/10/retry")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/rag/sources/RESUME/9/reindex")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, reindex);
    }

    private void assertNoPayload(ResultActions result, String prefix) throws Exception {
        for (String field : List.of("snapshotTitle", "snapshotContent", "sourceRevision", "title", "content",
                "target", "snapshot", "storageKey", "ownerUserId", "attemptId")) {
            result.andExpect(jsonPath(prefix + "." + field).doesNotExist());
        }
    }

    private AdminRagResponse.Source source() {
        return new AdminRagResponse.Source(1, RagSourceType.RESUME, 9, 3, null, 0, 0);
    }

    private AdminRagResponse.Job job() {
        Instant now = Instant.parse("2026-09-17T00:00:00Z");
        return new AdminRagResponse.Job(10, RagSourceType.RESUME, 9, 3,
                RagIndexOperation.UPSERT, RagIndexJobStatus.PENDING, 3, 6, 1, null, now, null, now, now);
    }
}
