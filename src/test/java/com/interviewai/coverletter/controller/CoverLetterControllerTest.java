package com.interviewai.coverletter.controller;

import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.coverletter.dto.CoverLetterResponse;
import com.interviewai.coverletter.dto.CoverLetterSummaryResponse;
import com.interviewai.coverletter.dto.CreateCoverLetterRequest;
import com.interviewai.coverletter.dto.UpdateCoverLetterRequest;
import com.interviewai.coverletter.exception.CoverLetterNotFoundException;
import com.interviewai.coverletter.exception.CoverLetterVersionNotFoundException;
import com.interviewai.coverletter.exception.RepresentativeCoverLetterNotFoundException;
import com.interviewai.coverletter.service.CoverLetterService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CoverLetterController.class)
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
class CoverLetterControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long COVER_LETTER_ID = 10L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 3, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoverLetterService coverLetterService;
    @MockitoBean
    private OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;
    @MockitoBean
    private OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;
    @MockitoBean
    private GithubOAuth2UserService githubOAuth2UserService;


    @Test
    @DisplayName("JWT 인증 사용자가 자기소개서를 생성하면 201을 반환한다")
    void createsCoverLetter() throws Exception {
        CreateCoverLetterRequest request = new CreateCoverLetterRequest("지원서", "지원 동기");
        CoverLetterResponse response = response();
        when(coverLetterService.create(USER_ID.toString(), request)).thenReturn(response);

        mockMvc.perform(post("/api/cover-letters")
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"지원서\",\"content\":\"지원 동기\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(COVER_LETTER_ID))
                .andExpect(jsonPath("$.currentVersionNumber").value(1))
                .andExpect(jsonPath("$.representative").value(false));

        verify(coverLetterService).create(USER_ID.toString(), request);
    }


    @Test
    @DisplayName("제목 앞뒤 공백은 제거해서 서비스에 전달한다")
    void trimsTitle() throws Exception {
        CreateCoverLetterRequest request = new CreateCoverLetterRequest("지원서", "  본문은 보존  ");
        when(coverLetterService.create(USER_ID.toString(), request)).thenReturn(response());

        mockMvc.perform(post("/api/cover-letters")
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  지원서  \",\"content\":\"  본문은 보존  \"}"))
                .andExpect(status().isCreated());

        verify(coverLetterService).create(USER_ID.toString(), request);
    }


    @Test
    @DisplayName("빈 제목과 본문은 validation 오류를 반환한다")
    void rejectsBlankFields() throws Exception {
        mockMvc.perform(post("/api/cover-letters")
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \",\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.title").value("제목은 필수입니다."))
                .andExpect(jsonPath("$.errors.content").value("본문은 필수입니다."));

        verifyNoInteractions(coverLetterService);
    }


    @Test
    @DisplayName("제목과 본문 최대 길이 경계를 허용한다")
    void acceptsMaximumLengths() throws Exception {
        String title = "가".repeat(100);
        String content = "나".repeat(20000);
        CreateCoverLetterRequest request = new CreateCoverLetterRequest(title, content);
        when(coverLetterService.create(USER_ID.toString(), request)).thenReturn(response());

        mockMvc.perform(post("/api/cover-letters")
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated());

        verify(coverLetterService).create(USER_ID.toString(), request);
    }


    @Test
    @DisplayName("제목 또는 본문이 최대 길이를 초과하면 거부한다")
    void rejectsFieldsOverMaximumLength() throws Exception {
        String title = "가".repeat(101);
        String content = "나".repeat(20001);

        mockMvc.perform(post("/api/cover-letters")
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("제목은 100자 이하여야 합니다."))
                .andExpect(jsonPath("$.errors.content").value("본문은 20,000자 이하여야 합니다."));

        verifyNoInteractions(coverLetterService);
    }


    @Test
    @DisplayName("JWT가 없으면 자기소개서 API에 접근할 수 없다")
    void rejectsRequestWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/cover-letters"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(coverLetterService);
    }


    @Test
    @DisplayName("자기소개서 목록을 반환한다")
    void returnsCoverLetters() throws Exception {
        when(coverLetterService.getAll(USER_ID.toString())).thenReturn(List.of(
                new CoverLetterSummaryResponse(
                        COVER_LETTER_ID, "지원서", 2, true, CREATED_AT, CREATED_AT
                )
        ));

        mockMvc.perform(get("/api/cover-letters")
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(COVER_LETTER_ID))
                .andExpect(jsonPath("$[0].representative").value(true));
    }


    @Test
    @DisplayName("소유하지 않은 자기소개서는 404를 반환한다")
    void returnsNotFoundForUnownedCoverLetter() throws Exception {
        when(coverLetterService.get(USER_ID.toString(), COVER_LETTER_ID))
                .thenThrow(new CoverLetterNotFoundException());

        mockMvc.perform(get("/api/cover-letters/{id}", COVER_LETTER_ID)
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COVER_LETTER_NOT_FOUND"));
    }


    @Test
    @DisplayName("자기소개서를 수정하면 새 현재 버전을 반환한다")
    void updatesCoverLetter() throws Exception {
        UpdateCoverLetterRequest request = new UpdateCoverLetterRequest("수정 제목", "수정 본문");
        CoverLetterResponse response = new CoverLetterResponse(
                COVER_LETTER_ID, "수정 제목", "수정 본문", 2, false, CREATED_AT, CREATED_AT
        );
        when(coverLetterService.update(USER_ID.toString(), COVER_LETTER_ID, request))
                .thenReturn(response);

        mockMvc.perform(put("/api/cover-letters/{id}", COVER_LETTER_ID)
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"수정 제목\",\"content\":\"수정 본문\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersionNumber").value(2));

        verify(coverLetterService).update(USER_ID.toString(), COVER_LETTER_ID, request);
    }


    @Test
    @DisplayName("없는 버전 조회는 전용 오류 코드를 반환한다")
    void returnsNotFoundForUnknownVersion() throws Exception {
        when(coverLetterService.getVersion(USER_ID.toString(), COVER_LETTER_ID, 99))
                .thenThrow(new CoverLetterVersionNotFoundException());

        mockMvc.perform(get("/api/cover-letters/{id}/versions/{version}", COVER_LETTER_ID, 99)
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COVER_LETTER_VERSION_NOT_FOUND"));
    }


    @Test
    @DisplayName("과거 버전을 복원하면 새 버전을 반환한다")
    void restoresVersion() throws Exception {
        CoverLetterResponse restored = new CoverLetterResponse(
                COVER_LETTER_ID, "과거 제목", "과거 본문", 3, false, CREATED_AT, CREATED_AT
        );
        when(coverLetterService.restore(USER_ID.toString(), COVER_LETTER_ID, 1))
                .thenReturn(restored);

        mockMvc.perform(post("/api/cover-letters/{id}/versions/{version}/restore", COVER_LETTER_ID, 1)
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersionNumber").value(3))
                .andExpect(jsonPath("$.content").value("과거 본문"));
    }


    @Test
    @DisplayName("대표 자기소개서를 설정하고 해제하면 204를 반환한다")
    void setsAndClearsRepresentative() throws Exception {
        mockMvc.perform(put("/api/cover-letters/{id}/representative", COVER_LETTER_ID)
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(delete("/api/cover-letters/representative")
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(coverLetterService).setRepresentative(USER_ID.toString(), COVER_LETTER_ID);
        verify(coverLetterService).clearRepresentative(USER_ID.toString());
    }


    @Test
    @DisplayName("대표 자기소개서가 없으면 404를 반환한다")
    void returnsNotFoundWhenRepresentativeIsMissing() throws Exception {
        when(coverLetterService.getRepresentative(USER_ID.toString()))
                .thenThrow(new RepresentativeCoverLetterNotFoundException());

        mockMvc.perform(get("/api/cover-letters/representative")
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPRESENTATIVE_COVER_LETTER_NOT_FOUND"));
    }


    @Test
    @DisplayName("자기소개서를 삭제하면 204를 반환한다")
    void deletesCoverLetter() throws Exception {
        mockMvc.perform(delete("/api/cover-letters/{id}", COVER_LETTER_ID)
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(coverLetterService).delete(USER_ID.toString(), COVER_LETTER_ID);
    }


    private CoverLetterResponse response() {
        return new CoverLetterResponse(
                COVER_LETTER_ID,
                "지원서",
                "지원 동기",
                1,
                false,
                CREATED_AT,
                CREATED_AT
        );
    }
}
