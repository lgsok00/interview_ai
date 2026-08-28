package com.interviewai.auth.handler;

import com.interviewai.auth.dto.LoginResponse;
import com.interviewai.auth.exception.InvalidOAuth2UserException;
import com.interviewai.auth.exception.OAuth2EmailConflictException;
import com.interviewai.auth.service.GoogleOAuth2LoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.PRAGMA;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private GoogleOAuth2LoginService googleOAuth2LoginService;

    private ObjectMapper objectMapper;
    private OAuth2AuthenticationSuccessHandler successHandler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;


    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        successHandler = new OAuth2AuthenticationSuccessHandler(
                googleOAuth2LoginService,
                objectMapper
        );
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }


    @Test
    @DisplayName("Google 인증 성공 시 서비스가 발급한 토큰 응답을 JSON으로 반환한다")
    void returnsTokenResponseForGoogleAuthentication() throws Exception {
        OAuth2AuthenticationToken authentication = googleAuthentication();
        OidcUser oidcUser = oidcPrincipal(authentication);
        LoginResponse loginResponse = LoginResponse.bearer(
                "access-token",
                "refresh-token",
                3600,
                1209600
        );
        when(googleOAuth2LoginService.login(oidcUser))
                .thenReturn(loginResponse);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith(APPLICATION_JSON_VALUE);
        assertThat(response.getHeader(CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeader(PRAGMA)).isEqualTo("no-cache");
        assertThat(response.getContentAsString()).isEqualTo(
                objectMapper.writeValueAsString(loginResponse)
        );
        verify(googleOAuth2LoginService).login(oidcUser);
    }


    @Test
    @DisplayName("Google 이외 registration의 인증 성공은 거부한다")
    void rejectsAuthenticationFromUnsupportedRegistration() throws Exception {
        OAuth2AuthenticationToken authentication = authentication("github");

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_OAUTH2_USER");
        verifyNoInteractions(googleOAuth2LoginService);
    }


    @Test
    @DisplayName("기존 인증 방식과 이메일이 충돌하면 409 오류를 반환한다")
    void returnsConflictForEmailOwnedByAnotherProvider() throws Exception {
        OAuth2AuthenticationToken authentication = googleAuthentication();
        OidcUser oidcUser = oidcPrincipal(authentication);
        when(googleOAuth2LoginService.login(oidcUser))
                .thenThrow(new OAuth2EmailConflictException());

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString())
                .contains("OAUTH2_EMAIL_CONFLICT")
                .doesNotContain("access-token")
                .doesNotContain("refresh-token");
    }


    @Test
    @DisplayName("Google 사용자 정보가 유효하지 않으면 401 오류를 반환한다")
    void returnsUnauthorizedForInvalidGoogleUser() throws Exception {
        OAuth2AuthenticationToken authentication = googleAuthentication();
        OidcUser oidcUser = oidcPrincipal(authentication);
        when(googleOAuth2LoginService.login(oidcUser))
                .thenThrow(new InvalidOAuth2UserException());

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_OAUTH2_USER");
    }


    private OAuth2AuthenticationToken googleAuthentication() {
        return authentication("google");
    }


    private OidcUser oidcPrincipal(OAuth2AuthenticationToken authentication) {
        return (OidcUser) authentication.getPrincipal();
    }


    private OAuth2AuthenticationToken authentication(String registrationId) {
        Instant issuedAt = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "google-id-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of(
                        "sub", "google-subject-123",
                        "email", "user@example.com",
                        "email_verified", true,
                        "name", "Google 사용자"
                )
        );
        OidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken
        );

        return new OAuth2AuthenticationToken(
                oidcUser,
                oidcUser.getAuthorities(),
                registrationId
        );
    }
}
