package com.interviewai.global.config;

import com.interviewai.auth.controller.AuthController;
import com.interviewai.auth.dto.LoginResponse;
import com.interviewai.auth.dto.SignupResponse;
import com.interviewai.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        SecurityConfigTest.ProtectedTestController.class
})
@TestPropertySource(properties = {
        "auth.jwt.secret=test-jwt-secret-that-is-at-least-32-bytes-long",
        "auth.jwt.access-token-expiration=1h",
        "auth.jwt.refresh-token-expiration=14d"
})
class SecurityConfigTest {

    private final MockMvc mockMvc;
    private final JwtEncoder jwtEncoder;

    @MockitoBean
    private AuthService authService;


    @Autowired
    SecurityConfigTest(MockMvc mockMvc, JwtEncoder jwtEncoder) {
        this.mockMvc = mockMvc;
        this.jwtEncoder = jwtEncoder;
    }


    @Test
    @DisplayName("회원가입 endpoint는 인증 없이 접근할 수 있다")
    void allowsSignupWithoutAuthentication() throws Exception {
        when(authService.signup(any()))
                .thenReturn(new SignupResponse(
                        1L,
                        "user@example.com",
                        "테스트유저"
                ));

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "user@example.com",
                                          "password": "password123",
                                          "nickname": "테스트유저"
                                        }
                                        """)
                )
                .andExpect(status().isCreated());
    }


    @Test
    @DisplayName("로그인 endpoint는 인증 없이 접근할 수 있다")
    void allowsLoginWithoutAuthentication() throws Exception {
        when(authService.login(any()))
                .thenReturn(LoginResponse.bearer(
                        "access-token",
                        "refresh-token",
                        3600,
                        1209600
                ));

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "user@example.com",
                                          "password": "password123"
                                        }
                                        """)
                )
                .andExpect(status().isOk());
    }


    @Test
    @DisplayName("보호된 endpoint는 토큰이 없으면 401을 반환한다")
    void rejectsProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/security-test/protected"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("유효한 JWT가 있으면 보호된 endpoint에 접근할 수 있다")
    void allowsProtectedEndpointWithValidJwt() throws Exception {
        String accessToken = issueAccessToken();

        mockMvc.perform(
                        get("/api/security-test/protected")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(content().string("authenticated"));
    }


    @Test
    @DisplayName("Refresh Token 재발급 endpoint는 인증 없이 접근할 수 있다")
    void allowsRefreshWithoutAuthentication() throws Exception {
        when(authService.refresh(any()))
                .thenReturn(
                        LoginResponse.bearer(
                                "new-access-token",
                                "new-refresh-token",
                                3600,
                                1209600
                        )
                );

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "old-refresh-token"
                                        }
                                        """)
                )
                .andExpect(status().isOk());
    }


    @Test
    @DisplayName("로그아웃 endpoint는 인증 없이 접근할 수 있다")
    void allowsLogoutWithoutAuthentication() throws Exception {
        mockMvc.perform(
                        post("/api/auth/logout")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "refresh-token"
                                        }
                                        """)
                )
                .andExpect(status().isNoContent());
    }


    @Test
    @DisplayName("전체 세션 폐기 endpoint는 인증이 없으면 접근할 수 없다")
    void rejectsLogoutAllWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout-all"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }


    @Test
    @DisplayName("전체 세션 폐기 endpoint는 JWT 인증 사용자의 subject를 전달한다")
    void allowsLogoutAllWithValidJwt() throws Exception {
        mockMvc.perform(
                        post("/api/auth/logout-all")
                                .with(jwt().jwt(jwt -> jwt.subject("1")))
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(authService).logoutAll("1");
    }


    private String issueAccessToken() {
        Instant issuedAt = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("interview-ai")
                .subject("1")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("email", "user@example.com")
                .claim("role", "USER")
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }


    @RestController
    static class ProtectedTestController {

        @GetMapping("/api/security-test/protected")
        ResponseEntity<String> protectedEndpoint() {
            return ResponseEntity.ok("authenticated");
        }
    }
}
