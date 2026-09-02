package com.interviewai.user.controller;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.user.dto.CurrentUserResponse;
import com.interviewai.user.dto.ChangePasswordRequest;
import com.interviewai.user.dto.UpdateUserRequest;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.exception.InvalidCurrentPasswordException;
import com.interviewai.user.exception.PasswordChangeNotSupportedException;
import com.interviewai.user.exception.SamePasswordException;
import com.interviewai.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
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
class UserControllerTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String NICKNAME = "테스트유저";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;

    @MockitoBean
    private OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;

    @MockitoBean
    private GithubOAuth2UserService githubOAuth2UserService;


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


    @Test
    @DisplayName("JWT 인증 사용자의 닉네임을 수정한다")
    void updatesCurrentUser() throws Exception {
        String updatedNickname = "수정된닉네임";
        CurrentUserResponse response = new CurrentUserResponse(
                USER_ID,
                EMAIL,
                updatedNickname,
                AuthProvider.LOCAL,
                UserRole.USER
        );

        when(userService.updateCurrentUser(
                USER_ID.toString(),
                new UpdateUserRequest(updatedNickname)
        )).thenReturn(response);

        mockMvc.perform(
                        put("/api/users/me")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"수정된닉네임\"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.nickname").value(updatedNickname))
                .andExpect(jsonPath("$.provider").value("LOCAL"))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(userService).updateCurrentUser(
                USER_ID.toString(),
                new UpdateUserRequest(updatedNickname)
        );
    }


    @Test
    @DisplayName("닉네임의 앞뒤 공백을 제거한 뒤 수정한다")
    void trimsNicknameBeforeUpdate() throws Exception {
        String trimmedNickname = "수정닉네임";
        CurrentUserResponse response = new CurrentUserResponse(
                USER_ID,
                EMAIL,
                trimmedNickname,
                AuthProvider.LOCAL,
                UserRole.USER
        );

        when(userService.updateCurrentUser(
                USER_ID.toString(),
                new UpdateUserRequest(trimmedNickname)
        )).thenReturn(response);

        mockMvc.perform(
                        put("/api/users/me")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"  수정닉네임  \"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(trimmedNickname));

        verify(userService).updateCurrentUser(
                USER_ID.toString(),
                new UpdateUserRequest(trimmedNickname)
        );
    }


    @Test
    @DisplayName("2자와 50자 닉네임을 허용한다")
    void acceptsNicknameBoundaryLengths() throws Exception {
        String minNickname = "가나";
        String maxNickname = "가".repeat(50);

        when(userService.updateCurrentUser(
                USER_ID.toString(),
                new UpdateUserRequest(minNickname)
        )).thenReturn(new CurrentUserResponse(
                USER_ID, EMAIL, minNickname, AuthProvider.LOCAL, UserRole.USER
        ));
        when(userService.updateCurrentUser(
                USER_ID.toString(),
                new UpdateUserRequest(maxNickname)
        )).thenReturn(new CurrentUserResponse(
                USER_ID, EMAIL, maxNickname, AuthProvider.LOCAL, UserRole.USER
        ));

        mockMvc.perform(
                        put("/api/users/me")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"" + minNickname + "\"}")
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        put("/api/users/me")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"" + maxNickname + "\"}")
                )
                .andExpect(status().isOk());
    }


    @Test
    @DisplayName("공백 닉네임이면 400을 반환한다")
    void rejectsBlankNickname() throws Exception {
        mockMvc.perform(
                        put("/api/users/me")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"   \"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.nickname").isNotEmpty());

        verifyNoInteractions(userService);
    }


    @Test
    @DisplayName("닉네임이 1자이거나 50자를 초과하면 400을 반환한다")
    void rejectsNicknameOutsideLengthBoundary() throws Exception {
        String tooLongNickname = "가".repeat(51);

        mockMvc.perform(
                        put("/api/users/me")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"가\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nickname")
                        .value("닉네임은 2자 이상 50자 이하여야 합니다."));

        mockMvc.perform(
                        put("/api/users/me")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"" + tooLongNickname + "\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nickname")
                        .value("닉네임은 2자 이상 50자 이하여야 합니다."));

        verifyNoInteractions(userService);
    }


    @Test
    @DisplayName("JWT가 없으면 사용자 정보를 수정할 수 없다")
    void rejectsUpdateWithoutJwt() throws Exception {
        mockMvc.perform(
                        put("/api/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"수정닉네임\"}")
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }


    @Test
    @DisplayName("수정할 사용자가 없으면 404를 반환한다")
    void returnsNotFoundWhenUpdatedUserDoesNotExist() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest("수정닉네임");

        when(userService.updateCurrentUser(USER_ID.toString(), request))
                .thenThrow(new UserNotFoundException());

        mockMvc.perform(
                        put("/api/users/me")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"수정닉네임\"}")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        verify(userService).updateCurrentUser(USER_ID.toString(), request);
    }


    @Test
    @DisplayName("잘못된 JWT subject이면 사용자 정보 수정 시 401을 반환한다")
    void rejectsUpdateWithInvalidJwtSubject() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest("수정닉네임");

        when(userService.updateCurrentUser("invalid-user-id", request))
                .thenThrow(new InvalidAccessTokenException());

        mockMvc.perform(
                        put("/api/users/me")
                                .with(jwt().jwt(jwt -> jwt.subject("invalid-user-id")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"수정닉네임\"}")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

        verify(userService).updateCurrentUser("invalid-user-id", request);
    }


    @Test
    @DisplayName("JWT 인증 로컬 사용자의 비밀번호를 변경하면 204를 반환한다")
    void changesPassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("password123", "new-password456");

        mockMvc.perform(
                        put("/api/users/me/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "currentPassword": "password123",
                                          "newPassword": "new-password456"
                                        }
                                        """)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userService).changePassword(USER_ID.toString(), request);
    }


    @Test
    @DisplayName("현재 비밀번호가 틀리면 401을 반환한다")
    void rejectsInvalidCurrentPassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("wrong-password", "new-password456");
        doThrow(new InvalidCurrentPasswordException())
                .when(userService).changePassword(USER_ID.toString(), request);

        mockMvc.perform(
                        put("/api/users/me/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "currentPassword": "wrong-password",
                                          "newPassword": "new-password456"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"))
                .andExpect(jsonPath("$.message").value("현재 비밀번호가 올바르지 않습니다."));
    }


    @Test
    @DisplayName("현재 비밀번호와 새 비밀번호가 같으면 400을 반환한다")
    void rejectsSamePassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("password123", "password123");
        doThrow(new SamePasswordException())
                .when(userService).changePassword(USER_ID.toString(), request);

        mockMvc.perform(
                        put("/api/users/me/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "currentPassword": "password123",
                                          "newPassword": "password123"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_PASSWORD"));
    }


    @Test
    @DisplayName("OAuth2 사용자의 비밀번호 변경 요청은 400을 반환한다")
    void rejectsOAuth2User() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("password123", "new-password456");
        doThrow(new PasswordChangeNotSupportedException())
                .when(userService).changePassword(USER_ID.toString(), request);

        mockMvc.perform(
                        put("/api/users/me/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "currentPassword": "password123",
                                          "newPassword": "new-password456"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_NOT_SUPPORTED"));
    }


    @Test
    @DisplayName("새 비밀번호는 8자와 64자 경계값을 허용한다")
    void acceptsNewPasswordBoundaryLengths() throws Exception {
        String minPassword = "a".repeat(8);
        String maxPassword = "a".repeat(64);

        mockMvc.perform(
                        put("/api/users/me/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"password123\",\"newPassword\":\"" + minPassword + "\"}")
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        put("/api/users/me/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"password123\",\"newPassword\":\"" + maxPassword + "\"}")
                )
                .andExpect(status().isNoContent());

        verify(userService).changePassword(
                USER_ID.toString(), new ChangePasswordRequest("password123", minPassword)
        );
        verify(userService).changePassword(
                USER_ID.toString(), new ChangePasswordRequest("password123", maxPassword)
        );
    }


    @Test
    @DisplayName("현재 비밀번호가 비어 있으면 400을 반환한다")
    void rejectsBlankCurrentPassword() throws Exception {
        mockMvc.perform(
                        put("/api/users/me/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"\",\"newPassword\":\"new-password456\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.currentPassword").value("현재 비밀번호는 필수입니다."));

        verifyNoInteractions(userService);
    }


    @Test
    @DisplayName("새 비밀번호가 8자 미만이거나 64자를 초과하면 400을 반환한다")
    void rejectsNewPasswordOutsideLengthBoundary() throws Exception {
        mockMvc.perform(
                        put("/api/users/me/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"password123\",\"newPassword\":\"short\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword")
                        .value("새 비밀번호는 8자 이상 64자 이하여야 합니다."));

        String tooLongPassword = "a".repeat(65);
        mockMvc.perform(
                        put("/api/users/me/password")
                                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"password123\",\"newPassword\":\"" + tooLongPassword + "\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword")
                        .value("새 비밀번호는 8자 이상 64자 이하여야 합니다."));

        verifyNoInteractions(userService);
    }


    @Test
    @DisplayName("JWT가 없으면 비밀번호를 변경할 수 없다")
    void rejectsPasswordChangeWithoutJwt() throws Exception {
        mockMvc.perform(
                        put("/api/users/me/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "currentPassword": "password123",
                                          "newPassword": "new-password456"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }
}
