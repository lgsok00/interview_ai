package com.interviewai.user.controller;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.user.dto.CurrentUserResponse;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "auth.jwt.secret=test-jwt-secret-that-is-at-least-32-bytes-long",
        "auth.jwt.access-token-expiration=1h",
        "auth.jwt.refresh-token-expiration=14d"
})
class UserControllerTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String NICKNAME = "테스트유저";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;


    @Test
    @DisplayName("JWT 인증 사용자의 정보를 반환한다")
    void returnsCurrentUser() throws Exception {
        CurrentUserResponse response = new CurrentUserResponse(
                USER_ID,
                EMAIL,
                NICKNAME,
                AuthProvider.LOCAL,
                UserRole.USER
        );

        when(userService.getCurrentUser(USER_ID.toString()))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/users/me")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/json"
                ))
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.nickname").value(NICKNAME))
                .andExpect(jsonPath("$.provider").value("LOCAL"))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(userService).getCurrentUser(USER_ID.toString());
    }


    @Test
    @DisplayName("JWT가 없으면 사용자 정보를 조회할 수 없다")
    void rejectsRequestWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }


    @Test
    @DisplayName("JWT 사용자와 일치하는 사용자가 없으면 404를 반환한다")
    void returnsNotFoundWhenUserDoesNotExist() throws Exception {
        when(userService.getCurrentUser(USER_ID.toString()))
                .thenThrow(new UserNotFoundException());

        mockMvc.perform(
                        get("/api/users/me")
                                .with(jwt().jwt(jwt ->
                                        jwt.subject(USER_ID.toString())
                                ))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("사용자를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.errors").isMap());

        verify(userService).getCurrentUser(USER_ID.toString());
    }


    @Test
    @DisplayName("잘못된 JWT subject이면 401을 반환한다")
    void rejectsInvalidJwtSubject() throws Exception {
        when(userService.getCurrentUser("invalid-user-id"))
                .thenThrow(new InvalidAccessTokenException());

        mockMvc.perform(
                        get("/api/users/me")
                                .with(jwt().jwt(jwt ->
                                        jwt.subject("invalid-user-id")
                                ))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_ACCESS_TOKEN"))
                .andExpect(jsonPath("$.message")
                        .value("Access Token의 인증 정보가 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors").isMap());

        verify(userService).getCurrentUser("invalid-user-id");
    }
}
