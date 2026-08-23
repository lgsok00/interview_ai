package com.interviewai.auth.service;

import com.interviewai.auth.dto.LoginRequest;
import com.interviewai.auth.dto.LoginResponse;
import com.interviewai.auth.dto.SignupRequest;
import com.interviewai.auth.dto.SignupResponse;
import com.interviewai.auth.exception.DuplicateEmailException;
import com.interviewai.auth.exception.InvalidCredentialsException;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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

    private AuthService authService;


    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenService);
    }


    @Nested
    class Signup {

        @Test
        void 회원가입에_성공한다() {
            SignupRequest request = new SignupRequest(" USER@EXAMPLE.COM ", RAW_PASSWORD, " 테스트 유저 ");

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
        void 비밀번호를_평문으로_저장하지_않는다() {
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
        void 이미_가입된_이메일이면_회원가입에_실패한다() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.signup(signupRequest())).isInstanceOf(DuplicateEmailException.class);

            verify(passwordEncoder, never()).encode(anyString());

            verify(userRepository, never()).saveAndFlush(any(User.class));
        }
    }


    @Nested
    class Login {

        @Test
        void 이메일과_비밀번호가_일치하면_JWT를_반환한다() {
            User user = localUser();

            LoginResponse expectedResponse = LoginResponse.bearer("access-token", 3600);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            when(jwtTokenService.issueAccessToken(user)).thenReturn(expectedResponse);

            LoginResponse response = authService.login(loginRequest());

            assertThat(response).isEqualTo(expectedResponse);

            verify(jwtTokenService).issueAccessToken(user);
        }


        @Test
        void 로그인할_때_이메일을_정규화한다() {
            User user = localUser();

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            when(jwtTokenService.issueAccessToken(user))
                    .thenReturn(LoginResponse.bearer("access-token", 3600));

            authService.login(new LoginRequest(" USER@EXAMPLE.COM ", RAW_PASSWORD));

            verify(userRepository).findByEmail(EMAIL);
        }


        @Test
        void 비밀번호가_일치하지_않으면_로그인에_실패한다() {
            User user = localUser();

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            when(passwordEncoder.matches("wrong-password", ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(jwtTokenService, never()).issueAccessToken(any(User.class));
        }


        @Test
        void 존재하지_않는_이메일이면_로그인에_실패한다() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginRequest())).isInstanceOf(InvalidCredentialsException.class);

            verify(passwordEncoder, never()).matches(anyString(), anyString());

            verify(jwtTokenService, never()).issueAccessToken(any(User.class));
        }
    }
}
