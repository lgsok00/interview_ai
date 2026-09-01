package com.interviewai.global.config;

import com.interviewai.auth.controller.AuthController;
import com.interviewai.auth.dto.LoginResponse;
import com.interviewai.auth.dto.SignupResponse;
import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.AuthService;
import com.interviewai.auth.service.GithubOAuth2UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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
class SecurityConfigTest {

    private final MockMvc mockMvc;
    private final JwtEncoder jwtEncoder;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;

    @MockitoBean
    private OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;

    @MockitoBean
    private GithubOAuth2UserService githubOAuth2UserService;


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


    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness"
    })
    @DisplayName("Health endpoint와 probe는 인증 없이 보안 필터를 통과한다")
    void allowsHealthEndpointsWithoutAuthentication(String endpoint) throws Exception {
        MvcResult result = mockMvc.perform(get(endpoint))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }


    @Test
    @DisplayName("Health 이외의 Actuator endpoint는 인증 없이 접근할 수 없다")
    void rejectsOtherActuatorEndpointsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
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


    @Test
    @DisplayName("Google OAuth2 인증 시작 요청은 Google로 리다이렉트하고 세션을 생성한다")
    void redirectsGoogleAuthorizationRequestAndCreatesSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
                .contains("client_id=test-google-client-id")
                .contains("redirect_uri=http://localhost/login/oauth2/code/google")
                .contains("state=");
        assertThat(result.getRequest().getSession(false)).isNotNull();
    }


    @Test
    @DisplayName("Google OAuth2 callback 실패는 지정한 실패 handler로 전달한다")
    void delegatesGoogleCallbackFailureToFailureHandler() throws Exception {
        mockMvc.perform(
                        get("/login/oauth2/code/google")
                                .param("error", "access_denied")
                                .param("error_description", "The user denied access")
                )
                .andExpect(status().isOk());

        verify(oauth2AuthenticationFailureHandler)
                .onAuthenticationFailure(any(), any(), any());
    }


    @Test
    @DisplayName("GitHub OAuth2 인증 시작 요청은 GitHub로 리다이렉트하고 세션을 생성한다")
    void redirectsGithubAuthorizationRequestAndCreatesSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/github"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith("https://github.com/login/oauth/authorize?")
                .contains("client_id=test-github-client-id")
                .contains("redirect_uri=http://localhost/login/oauth2/code/github")
                .contains("scope=read:user%20user:email")
                .contains("state=");
        assertThat(result.getRequest().getSession(false)).isNotNull();
    }


    @Test
    @DisplayName("GitHub OAuth2 callback 실패는 지정한 실패 handler로 전달한다")
    void delegatesGithubCallbackFailureToFailureHandler() throws Exception {
        mockMvc.perform(
                        get("/login/oauth2/code/github")
                                .param("error", "access_denied")
                                .param("error_description", "The user denied access")
                )
                .andExpect(status().isOk());

        verify(oauth2AuthenticationFailureHandler)
                .onAuthenticationFailure(any(), any(), any());
    }


    @Test
    @DisplayName("일반 API의 stateless filter chain은 HTTP 세션을 생성하지 않는다")
    void doesNotCreateSessionForStatelessApiRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/security-test/protected"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
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
