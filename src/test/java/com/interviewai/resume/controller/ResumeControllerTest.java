package com.interviewai.resume.controller;

import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.resume.dto.ResumeResponse;
import com.interviewai.resume.enums.ResumeExtractionStatus;
import com.interviewai.resume.exception.ResumeFileTooLargeException;
import com.interviewai.resume.exception.ResumeNotFoundException;
import com.interviewai.resume.file.ResumeDownload;
import com.interviewai.resume.service.ResumeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ResumeController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "auth.jwt.secret=test-jwt-secret-that-is-at-least-32-bytes-long",
        "auth.jwt.access-token-expiration=1h",
        "auth.jwt.refresh-token-expiration=14d",
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret",
        "spring.security.oauth2.client.registration.github.client-id=test-github-client-id",
        "spring.security.oauth2.client.registration.github.client-secret=test-github-client-secret"
})
class ResumeControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long RESUME_ID = 10L;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ResumeService resumeService;
    @MockitoBean private OAuth2AuthenticationSuccessHandler successHandler;
    @MockitoBean private OAuth2AuthenticationFailureHandler failureHandler;
    @MockitoBean private GithubOAuth2UserService githubOAuth2UserService;


    @Test
    @DisplayName("인증 사용자가 multipart PDF 이력서를 등록하면 201을 반환한다")
    void createsResume() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata", "", "application/json", "{\"title\":\"백엔드 이력서\"}".getBytes()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-test".getBytes()
        );
        when(resumeService.create(eq(USER_ID.toString()), any(), any())).thenReturn(response());

        mockMvc.perform(multipart("/api/resumes")
                        .file(metadata)
                        .file(file)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(RESUME_ID))
                .andExpect(jsonPath("$.title").value("백엔드 이력서"))
                .andExpect(jsonPath("$.extractionStatus").value("COMPLETED"));
    }


    @Test
    @DisplayName("빈 이력서 제목은 validation 오류를 반환한다")
    void rejectsBlankTitle() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata", "", "application/json", "{\"title\":\"   \"}".getBytes()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-test".getBytes()
        );

        mockMvc.perform(multipart("/api/resumes")
                        .file(metadata)
                        .file(file)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.title").value("제목은 필수입니다."));

        verifyNoInteractions(resumeService);
    }


    @Test
    @DisplayName("소유하지 않은 이력서 조회는 404 전용 오류를 반환한다")
    void hidesOtherUsersResume() throws Exception {
        when(resumeService.get(USER_ID.toString(), RESUME_ID))
                .thenThrow(new ResumeNotFoundException());

        mockMvc.perform(get("/api/resumes/{id}", RESUME_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESUME_NOT_FOUND"));
    }


    @Test
    @DisplayName("이력서 원본 PDF를 첨부 파일로 다운로드한다")
    void downloadsResume() throws Exception {
        byte[] contents = "%PDF-download".getBytes();
        when(resumeService.download(USER_ID.toString(), RESUME_ID))
                .thenReturn(new ResumeDownload("내 이력서.pdf", "application/pdf", contents));

        mockMvc.perform(get("/api/resumes/{id}/file", RESUME_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().bytes(contents));
    }


    @Test
    @DisplayName("용량 제한 초과는 HTTP 413을 반환한다")
    void returnsContentTooLarge() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata", "", "application/json", "{\"title\":\"이력서\"}".getBytes()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-test".getBytes()
        );
        when(resumeService.create(eq(USER_ID.toString()), any(), any()))
                .thenThrow(new ResumeFileTooLargeException());

        mockMvc.perform(multipart("/api/resumes")
                        .file(metadata)
                        .file(file)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("RESUME_FILE_TOO_LARGE"));
    }


    @Test
    @DisplayName("대표 설정과 이력서 삭제는 빈 204 응답을 반환한다")
    void setsRepresentativeAndDeletesResume() throws Exception {
        mockMvc.perform(put("/api/resumes/{id}/representative", RESUME_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(delete("/api/resumes/{id}", RESUME_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(resumeService).setRepresentative(USER_ID.toString(), RESUME_ID);
        verify(resumeService).delete(USER_ID.toString(), RESUME_ID);
    }


    private ResumeResponse response() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 12, 0);
        return new ResumeResponse(
                RESUME_ID, "백엔드 이력서", "resume.pdf", "application/pdf",
                1024, "a".repeat(64), "resume text", ResumeExtractionStatus.COMPLETED,
                null, false, now, now
        );
    }
}
