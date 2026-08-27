package com.interviewai.auth.service;

import com.interviewai.auth.dto.*;
import com.interviewai.auth.exception.DuplicateEmailException;
import com.interviewai.auth.exception.InvalidCredentialsException;
import com.interviewai.auth.exception.InvalidRefreshTokenException;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static com.interviewai.support.AuthFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;


    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenService, refreshTokenService);
    }


    @Nested
    class Signup {

        @Test
        @DisplayName("회원가입에 성공한다")
        void signsUpSuccessfully() {
            SignupRequest request = new SignupRequest(" USER@EXAMPLE.COM ", RAW_PASSWORD, " 테스트유저 ");

            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

            when(userRepository.saveAndFlush(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            SignupResponse response = authService.signup(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

            verify(userRepository).saveAndFlush(userCaptor.capture());

            User savedUser = userCaptor.getValue();

            assertThat(savedUser.getEmail()).isEqualTo(EMAIL);
            assertThat(savedUser.getPasswordHash()).isEqualTo(ENCODED_PASSWORD);
            assertThat(savedUser.getNickname()).isEqualTo(NICKNAME);
            assertThat(savedUser.getProvider().name()).isEqualTo("LOCAL");
            assertThat(savedUser.getRole().name()).isEqualTo("USER");
            assertThat(response.email()).isEqualTo(EMAIL);
            assertThat(response.nickname()).isEqualTo(NICKNAME);
        }


        @Test
        @DisplayName("비밀번호를 평문으로 저장하지 않는다")
        void doesNotStoreRawPassword() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

            when(userRepository.saveAndFlush(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            authService.signup(signupRequest());

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

            verify(userRepository).saveAndFlush(userCaptor.capture());

            assertThat(userCaptor.getValue().getPasswordHash())
                    .isEqualTo(ENCODED_PASSWORD)
                    .isNotEqualTo(RAW_PASSWORD);

            verify(passwordEncoder).encode(RAW_PASSWORD);
        }


        @Test
        @DisplayName("이미 가입된 이메일이면 회원가입에 실패한다")
        void rejectsDuplicateEmail() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.signup(signupRequest())).isInstanceOf(DuplicateEmailException.class);

            verify(passwordEncoder, never()).encode(anyString());

            verify(userRepository, never()).saveAndFlush(any(User.class));
        }
    }


    @Nested
    class Login {

        @Test
        @DisplayName("이메일과 비밀번호가 일치하면 Access Token과 Refresh Token을 반환한다")
        void returnsTokensWhenCredentialsMatch() {
            User user = localUser();

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            when(jwtTokenService.issueAccessToken(user))
                    .thenReturn(new JwtTokenService.IssuedAccessToken("access-token", 3600));

            when(refreshTokenService.issue(user))
                    .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", 1209600));

            LoginResponse response = authService.login(loginRequest());

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(3600);
            assertThat(response.refreshTokenExpiresIn()).isEqualTo(1209600);

            verify(jwtTokenService).issueAccessToken(user);
            verify(refreshTokenService).issue(user);
        }


        @Test
        @DisplayName("로그인할 때 이메일을 정규화한다")
        void normalizesEmailOnLogin() {
            User user = localUser();

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            when(jwtTokenService.issueAccessToken(user))
                    .thenReturn(new JwtTokenService.IssuedAccessToken("access-token", 3600));

            when(refreshTokenService.issue(user))
                    .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", 1209600));

            authService.login(new LoginRequest(" USER@EXAMPLE.COM ", RAW_PASSWORD));

            verify(userRepository).findByEmail(EMAIL);
        }


        @Test
        @DisplayName("비밀번호가 일치하지 않으면 로그인에 실패한다")
        void rejectsIncorrectPassword() {
            User user = localUser();

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            when(passwordEncoder.matches("wrong-password", ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(jwtTokenService, never()).issueAccessToken(any(User.class));

            verify(refreshTokenService, never()).issue(any(User.class));
        }


        @Test
        @DisplayName("존재하지 않는 이메일이면 로그인에 실패한다")
        void rejectsUnknownEmail() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginRequest())).isInstanceOf(InvalidCredentialsException.class);

            verify(passwordEncoder, never()).matches(anyString(), anyString());

            verify(jwtTokenService, never()).issueAccessToken(any(User.class));

            verify(refreshTokenService, never()).issue(any(User.class));
        }
    }


    @Nested
    class Refresh {

        @Test
        @DisplayName("유효한 Refresh Token을 회전하고 새 토큰 쌍을 반환한다")
        void rotatesRefreshTokenAndReturnsNewTokens() {
            User user = localUser();
            RefreshTokenRequest request = new RefreshTokenRequest("old-refresh-token");

            when(refreshTokenService.rotate("old-refresh-token"))
                    .thenReturn(
                            new RefreshTokenService.RotatedRefreshToken(
                                    user,
                                    "new-refresh-token",
                                    1209600
                            )
                    );

            when(jwtTokenService.issueAccessToken(user))
                    .thenReturn(
                            new JwtTokenService.IssuedAccessToken(
                                    "new-access-token",
                                    3600
                            )
                    );

            LoginResponse response = authService.refresh(request);

            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(3600);
            assertThat(response.refreshTokenExpiresIn()).isEqualTo(1209600);

            verify(refreshTokenService).rotate("old-refresh-token");
            verify(jwtTokenService).issueAccessToken(user);
        }


        @Test
        @DisplayName("Refresh Token이 유효하지 않으면 Access Token을 발급하지 않는다")
        void doesNotIssueAccessTokenForInvalidRefreshToken() {
            RefreshTokenRequest request = new RefreshTokenRequest("invalid-refresh-token");

            when(refreshTokenService.rotate("invalid-refresh-token"))
                    .thenThrow(new InvalidRefreshTokenException());

            assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(InvalidRefreshTokenException.class);

            verify(jwtTokenService, never()).issueAccessToken(any(User.class));
        }
    }


    @Nested
    class Logout {

        @Test
        @DisplayName("요청받은 Refresh Token을 폐기한다")
        void revokesRequestedRefreshToken() {
            RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");

            authService.logout(request);

            verify(refreshTokenService).revoke("refresh-token");
        }
    }
}
