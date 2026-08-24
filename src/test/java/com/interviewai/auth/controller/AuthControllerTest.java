package com.interviewai.auth.controller;

import com.interviewai.auth.dto.LoginResponse;
import com.interviewai.auth.dto.SignupResponse;
import com.interviewai.auth.exception.InvalidCredentialsException;
import com.interviewai.auth.service.AuthService;
import com.interviewai.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends ControllerTestSupport {

    private AuthService authService;


    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);

        setUpController(new AuthController(authService));
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
                    .thenReturn(LoginResponse.bearer("access-token", 3600));

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
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(3600));
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
}
