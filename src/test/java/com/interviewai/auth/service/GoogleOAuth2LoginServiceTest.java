package com.interviewai.auth.service;

import com.interviewai.auth.dto.LoginResponse;
import com.interviewai.auth.exception.InvalidOAuth2UserException;
import com.interviewai.auth.exception.OAuth2EmailConflictException;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2LoginServiceTest {

    private static final String PROVIDER_ID = "google-subject-123";
    private static final String EMAIL = "user@example.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private OidcUser oidcUser;

    private GoogleOAuth2LoginService googleOAuth2LoginService;


    @BeforeEach
    void setUp() {
        googleOAuth2LoginService = new GoogleOAuth2LoginService(
                userRepository, jwtTokenService, refreshTokenService
        );
    }


    @Test
    @DisplayName("기존 Google 사용자는 provider와 provider id로 조회해 로그인한다")
    void logsInExistingGoogleUser() {
        User user = googleUser();
        stubValidOidcUser();
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID))
                .thenReturn(Optional.of(user));
        stubIssuedTokens(user);

        LoginResponse response = googleOAuth2LoginService.login(oidcUser);

        assertTokenResponse(response);
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }


    @Test
    @DisplayName("신규 Google 사용자를 생성하고 이메일을 정규화한다")
    void createsNewGoogleUserAndNormalizesEmail() {
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(oidcUser.getSubject()).thenReturn(PROVIDER_ID);
        when(oidcUser.getEmail()).thenReturn(" USER@EXAMPLE.COM ");
        when(oidcUser.getFullName()).thenReturn(" Google 사용자 ");
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubAnyIssuedTokens();

        googleOAuth2LoginService.login(oidcUser);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(EMAIL);
        assertThat(savedUser.getNickname()).isEqualTo("Google 사용자");
        assertThat(savedUser.getPasswordHash()).isNull();
        assertThat(savedUser.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(savedUser.getProviderId()).isEqualTo(PROVIDER_ID);
        assertThat(savedUser.getRole().name()).isEqualTo("USER");
        verify(jwtTokenService).issueAccessToken(savedUser);
        verify(refreshTokenService).issue(savedUser);
    }


    @Test
    @DisplayName("Google 이름이 없으면 이메일 앞부분을 닉네임으로 사용한다")
    void usesEmailLocalPartWhenNameIsMissing() {
        stubValidOidcUser();
        when(oidcUser.getFullName()).thenReturn(" ");
        stubNewUserPersistence();

        googleOAuth2LoginService.login(oidcUser);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("user");
    }


    @Test
    @DisplayName("Google 이름은 DB 닉네임 제한인 50자로 자른다")
    void truncatesLongGoogleName() {
        stubValidOidcUser();
        when(oidcUser.getFullName()).thenReturn("가".repeat(51));
        stubNewUserPersistence();

        googleOAuth2LoginService.login(oidcUser);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNickname()).hasSize(50);
    }


    @Test
    @DisplayName("이미 다른 인증 방식으로 가입된 이메일은 자동 연결하지 않는다")
    void rejectsEmailOwnedByAnotherProvider() {
        stubValidOidcUser();
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> googleOAuth2LoginService.login(oidcUser))
                .isInstanceOf(OAuth2EmailConflictException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(jwtTokenService, refreshTokenService);
    }


    @Test
    @DisplayName("Google에서 검증되지 않은 이메일은 거부한다")
    void rejectsUnverifiedEmail() {
        when(oidcUser.getEmailVerified()).thenReturn(false);

        assertThatThrownBy(() -> googleOAuth2LoginService.login(oidcUser))
                .isInstanceOf(InvalidOAuth2UserException.class);
        verifyNoInteractions(userRepository, jwtTokenService, refreshTokenService);
    }


    @Test
    @DisplayName("Google subject가 없으면 거부한다")
    void rejectsMissingSubject() {
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(oidcUser.getSubject()).thenReturn(" ");

        assertThatThrownBy(() -> googleOAuth2LoginService.login(oidcUser))
                .isInstanceOf(InvalidOAuth2UserException.class);
        verifyNoInteractions(userRepository, jwtTokenService, refreshTokenService);
    }


    @Test
    @DisplayName("Google 이메일이 없으면 거부한다")
    void rejectsMissingEmail() {
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(oidcUser.getSubject()).thenReturn(PROVIDER_ID);
        when(oidcUser.getEmail()).thenReturn(null);

        assertThatThrownBy(() -> googleOAuth2LoginService.login(oidcUser))
                .isInstanceOf(InvalidOAuth2UserException.class);
        verifyNoInteractions(userRepository, jwtTokenService, refreshTokenService);
    }


    private void stubValidOidcUser() {
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(oidcUser.getSubject()).thenReturn(PROVIDER_ID);
        when(oidcUser.getEmail()).thenReturn(EMAIL);
    }


    private void stubNewUserPersistence() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubAnyIssuedTokens();
    }


    private User googleUser() {
        return User.createGoogleUser(EMAIL, "Google 사용자", PROVIDER_ID);
    }


    private void stubIssuedTokens(User user) {
        when(jwtTokenService.issueAccessToken(user))
                .thenReturn(new JwtTokenService.IssuedAccessToken("access-token", 3600));
        when(refreshTokenService.issue(user))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", 1209600));
    }


    private void stubAnyIssuedTokens() {
        when(jwtTokenService.issueAccessToken(any(User.class)))
                .thenReturn(new JwtTokenService.IssuedAccessToken("access-token", 3600));
        when(refreshTokenService.issue(any(User.class)))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", 1209600));
    }


    private void assertTokenResponse(LoginResponse response) {
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.refreshTokenExpiresIn()).isEqualTo(1209600);
    }
}
