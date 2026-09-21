package com.interviewai.auth.handler;

import com.interviewai.auth.exception.InvalidOAuth2UserException;
import com.interviewai.auth.exception.OAuth2EmailConflictException;
import com.interviewai.auth.http.RefreshTokenCookieService;
import com.interviewai.auth.service.AuthTokens;
import com.interviewai.auth.service.GithubOAuth2LoginService;
import com.interviewai.auth.service.GoogleOAuth2LoginService;
import com.interviewai.user.exception.UserSuspendedException;
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
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.PRAGMA;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private GoogleOAuth2LoginService googleOAuth2LoginService;

    @Mock
    private GithubOAuth2LoginService githubOAuth2LoginService;

    @Mock
    private RefreshTokenCookieService refreshTokenCookies;

    private OAuth2AuthenticationSuccessHandler successHandler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;


    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        successHandler = new OAuth2AuthenticationSuccessHandler(
                googleOAuth2LoginService,
                githubOAuth2LoginService,
                objectMapper,
                refreshTokenCookies,
                "http://localhost:5173/oauth/callback"
        );
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }


    @Test
    @DisplayName("Google 인증 성공 시 Refresh Token 쿠키를 설정하고 프론트로 리다이렉트한다")
    void redirectsGoogleAuthenticationWithRefreshTokenCookie() throws Exception {
        OAuth2AuthenticationToken authentication = googleAuthentication();
        OidcUser oidcUser = oidcPrincipal(authentication);
        AuthTokens tokens = new AuthTokens(
                "access-token",
                "refresh-token",
                3600,
                1209600
        );
        when(googleOAuth2LoginService.login(oidcUser))
                .thenReturn(tokens);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/oauth/callback");
        assertThat(response.getHeader(CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeader(PRAGMA)).isEqualTo("no-cache");
        assertThat(response.getContentAsString()).doesNotContain("access-token", "refresh-token");
        verify(refreshTokenCookies).write(response, "refresh-token", 1209600);
        verify(googleOAuth2LoginService).login(oidcUser);
    }


    @Test
    @DisplayName("지원하지 않는 registration의 인증 성공은 거부한다")
    void rejectsAuthenticationFromUnsupportedRegistration() throws Exception {
        OAuth2AuthenticationToken authentication = authentication("naver");

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_OAUTH2_USER");
        verifyNoInteractions(googleOAuth2LoginService, githubOAuth2LoginService);
    }


    @Test
    @DisplayName("GitHub 인증 성공 시 쿠키를 설정하고 검증된 이메일과 principal을 전달한다")
    void redirectsGithubAuthenticationWithRefreshTokenCookie() throws Exception {
        OAuth2AuthenticationToken authentication = githubAuthentication();
        OAuth2User oauth2User = authentication.getPrincipal();
        AuthTokens tokens = new AuthTokens(
                "github-access-token",
                "github-refresh-token",
                3600,
                1209600
        );
        when(githubOAuth2LoginService.login(oauth2User, "user@example.com"))
                .thenReturn(tokens);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/oauth/callback");
        assertThat(response.getHeader(CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeader(PRAGMA)).isEqualTo("no-cache");
        assertThat(response.getContentAsString()).doesNotContain("github-access-token", "github-refresh-token");
        verify(refreshTokenCookies).write(response, "github-refresh-token", 1209600);
        verify(githubOAuth2LoginService).login(oauth2User, "user@example.com");
        verifyNoInteractions(googleOAuth2LoginService);
    }


    @Test
    @DisplayName("GitHub 사용자 정보가 유효하지 않으면 401 오류를 반환한다")
    void returnsUnauthorizedForInvalidGithubUser() throws Exception {
        OAuth2AuthenticationToken authentication = githubAuthentication();
        OAuth2User oauth2User = authentication.getPrincipal();
        when(githubOAuth2LoginService.login(oauth2User, "user@example.com"))
                .thenThrow(new InvalidOAuth2UserException());

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_OAUTH2_USER");
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


    @Test
    @DisplayName("정지된 OAuth2 사용자는 403 오류를 반환한다")
    void returnsForbiddenForSuspendedUser() throws Exception {
        OAuth2AuthenticationToken authentication = googleAuthentication();
        OidcUser oidcUser = oidcPrincipal(authentication);
        when(googleOAuth2LoginService.login(oidcUser))
                .thenThrow(new UserSuspendedException());

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("USER_SUSPENDED");
    }


    private OAuth2AuthenticationToken googleAuthentication() {
        return authentication("google");
    }


    private OAuth2AuthenticationToken githubAuthentication() {
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "id", 12345678L,
                        "login", "github-user",
                        "verified_email", "user@example.com"
                ),
                "id"
        );

        return new OAuth2AuthenticationToken(
                oauth2User,
                oauth2User.getAuthorities(),
                "github"
        );
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
