package com.interviewai.auth.controller;

import com.interviewai.auth.config.RefreshTokenCookieProperties;
import com.interviewai.auth.dto.SignupResponse;
import com.interviewai.auth.exception.InvalidCredentialsException;
import com.interviewai.auth.exception.InvalidRefreshTokenException;
import com.interviewai.auth.http.RefreshTokenCookieService;
import com.interviewai.auth.service.AuthService;
import com.interviewai.auth.service.AuthTokens;
import com.interviewai.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest extends ControllerTestSupport {

    private AuthService authService;


    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);

        var cookieService = new RefreshTokenCookieService(
                new RefreshTokenCookieProperties("refresh_token", "/api/auth", "Strict", false)
        );

        setUpController(new AuthController(authService, cookieService));
    }


    @Nested
    class Signup {

        @Test
        @DisplayName("회원가입에 성공하면 201을 반환한다")
        void returnsCreatedWhenSignupSucceeds() throws Exception {
            when(authService.signup(any()))
                    .thenReturn(new SignupResponse(1L, "user@example.com", "테스트유저"));

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
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("user@example.com"))
                    .andExpect(jsonPath("$.nickname").value("테스트유저"));
        }


        @Test
        @DisplayName("이메일 형식이 잘못되면 400을 반환한다")
        void returnsBadRequestForInvalidSignupEmail() throws Exception {
            mockMvc.perform(
                            post("/api/auth/signup")
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                            {
                                              "email": "invalid-email",
                                              "password": "password123",
                                              "nickname": "테스트유저"
                                            }
                                            """)
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors.email")
                            .value("올바른 이메일 형식이 아닙니다."));
        }


        @Test
        @DisplayName("비밀번호가 8자보다 짧으면 400을 반환한다")
        void returnsBadRequestWhenSignupPasswordIsTooShort() throws Exception {
            mockMvc.perform(
                            post("/api/auth/signup")
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                            {
                                              "email": "user@example.com",
                                              "password": "short",
                                              "nickname": "테스트유저"
                                            }
                                            """)
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors.password").exists());
        }
    }


    @Nested
    class Login {

        @Test
        @DisplayName("로그인에 성공하면 토큰을 반환한다")
        void returnsTokenWhenLoginSucceeds() throws Exception {
            when(authService.login(any()))
                    .thenReturn(
                            new AuthTokens(
                                    "access-token",
                                    "refresh-token",
                                    3600,
                                    1209600
                            )
                    );

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
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(3600))
                    .andExpect(jsonPath("$.refreshTokenExpiresIn").doesNotExist())
                    .andExpect(header().string(
                            "Set-Cookie",
                            org.hamcrest.Matchers.allOf(
                                    org.hamcrest.Matchers.containsString("refresh_token=refresh-token"),
                                    org.hamcrest.Matchers.containsString("HttpOnly"),
                                    org.hamcrest.Matchers.containsString("SameSite=Strict"),
                                    org.hamcrest.Matchers.containsString("Path=/api/auth")
                            )
                    ));
        }


        @Test
        @DisplayName("로그인 정보가 틀리면 401을 반환한다")
        void returnsUnauthorizedForInvalidCredentials() throws Exception {
            when(authService.login(any())).thenThrow(new InvalidCredentialsException());

            mockMvc.perform(
                            post("/api/auth/login")
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                            {
                                              "email": "user@example.com",
                                              "password": "wrong-password"
                                            }
                                            """)
                    )
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
        }


        @Test
        @DisplayName("이메일이 비어 있으면 400을 반환한다")
        void returnsBadRequestWhenLoginEmailIsBlank() throws Exception {
            mockMvc.perform(
                            post("/api/auth/login")
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                            {
                                              "email": "",
                                              "password": "password123"
                                            }
                                            """)
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors.email").exists());
        }
    }


    @Nested
    class Refresh {

        @Test
        @DisplayName("유효한 Refresh Token이면 새 토큰 쌍을 반환한다")
        void returnsNewTokensForValidRefreshToken() throws Exception {
            when(authService.refresh(any()))
                    .thenReturn(
                            new AuthTokens(
                                    "new-access-token",
                                    "new-refresh-token",
                                    3600,
                                    1209600
                            )
                    );

            mockMvc.perform(
                            post("/api/auth/refresh")
                                    .cookie(new jakarta.servlet.http.Cookie(
                                            "refresh_token",
                                            "old-refresh-token"
                                    ))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(3600))
                    .andExpect(jsonPath("$.refreshTokenExpiresIn").doesNotExist())
                    .andExpect(header().string(
                            "Set-Cookie",
                            org.hamcrest.Matchers.containsString("refresh_token=new-refresh-token")
                    ));
        }


        @Test
        @DisplayName("Refresh Token 쿠키가 없으면 401을 반환한다")
        void returnsUnauthorizedWithoutRefreshTokenCookie() throws Exception {
            when(authService.refresh(null)).thenThrow(new InvalidRefreshTokenException());

            mockMvc.perform(post("/api/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        }


        @Test
        @DisplayName("Refresh Token이 유효하지 않으면 401을 반환한다")
        void returnsUnauthorizedForInvalidRefreshToken() throws Exception {
            when(authService.refresh(any())).thenThrow(new InvalidRefreshTokenException());

            mockMvc.perform(
                            post("/api/auth/refresh")
                                    .cookie(new jakarta.servlet.http.Cookie(
                                            "refresh_token",
                                            "invalid-refresh-token"
                                    ))
                    )
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code")
                            .value("INVALID_REFRESH_TOKEN"))
                    .andExpect(jsonPath("$.message")
                            .value("Refresh Token이 올바르지 않거나 만료되었습니다."));
        }
    }


    @Nested
    class Logout {

        @Test
        @DisplayName("로그아웃에 성공하면 204와 빈 응답을 반환한다")
        void returnsNoContentWhenLogoutSucceeds() throws Exception {
            mockMvc.perform(
                            post("/api/auth/logout")
                                    .cookie(new jakarta.servlet.http.Cookie(
                                            "refresh_token",
                                            "refresh-token"
                                    ))
                    )
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""))
                    .andExpect(header().string(
                            "Set-Cookie",
                            org.hamcrest.Matchers.allOf(
                                    org.hamcrest.Matchers.containsString("refresh_token="),
                                    org.hamcrest.Matchers.containsString("Max-Age=0")
                            )
                    ));

            verify(authService).logout("refresh-token");
        }


        @Test
        @DisplayName("Refresh Token 쿠키가 없어도 로그아웃은 멱등하게 성공한다")
        void returnsNoContentWithoutRefreshTokenCookie() throws Exception {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isNoContent());

            verify(authService).logout(null);
        }
    }
}
