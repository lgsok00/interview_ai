package com.interviewai.user.controller;

import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.global.error.CatalogException;
import com.interviewai.user.dto.AdminUserPageResponse;
import com.interviewai.user.dto.AdminUserResponse;
import com.interviewai.user.dto.ChangeUserRoleRequest;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
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
class AdminUserControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    AdminUserService service;
    @MockitoBean
    OAuth2AuthenticationSuccessHandler successHandler;
    @MockitoBean
    OAuth2AuthenticationFailureHandler failureHandler;
    @MockitoBean
    GithubOAuth2UserService githubService;

    @Test
    void bindsDefaultsAndReturnsPage() throws Exception {
        when(service.search("1", null, null, null, 0, 20))
                .thenReturn(new AdminUserPageResponse(List.of(response()), 0, 20, 1, 1));
        mvc.perform(get("/api/admin/users").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(2))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].providerId").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void bindsFilters() throws Exception {
        when(service.search("1", "user", UserRole.ADMIN, AuthProvider.GITHUB, 1, 10))
                .thenReturn(new AdminUserPageResponse(List.of(), 1, 10, 0, 0));
        mvc.perform(get("/api/admin/users")
                        .param("keyword", "user").param("role", "ADMIN").param("provider", "GITHUB")
                        .param("page", "1").param("size", "10")
                        .with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk());
        verify(service).search("1", "user", UserRole.ADMIN, AuthProvider.GITHUB, 1, 10);
    }

    @Test
    void returnsDetailAndChangedRole() throws Exception {
        when(service.get("1", 2L)).thenReturn(response());
        when(service.changeRole("1", 2L, new ChangeUserRoleRequest(UserRole.ADMIN)))
                .thenReturn(response());
        mvc.perform(get("/api/admin/users/2").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("user@example.com"));
        mvc.perform(patch("/api/admin/users/2/role").with(jwt().jwt(token -> token.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"role\":null}", "{\"role\":\"ROOT\"}", "{"})
    void rejectsInvalidRoleBodies(String body) throws Exception {
        mvc.perform(patch("/api/admin/users/2/role").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "provider", "page", "size"})
    void rejectsInvalidQueryTypes(String parameter) throws Exception {
        mvc.perform(get("/api/admin/users").param(parameter, "invalid").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void mapsForbiddenMissingAndConflict() throws Exception {
        when(service.get("1", 2L)).thenThrow(new CatalogException(HttpStatus.FORBIDDEN, "FORBIDDEN", "금지"));
        mvc.perform(get("/api/admin/users/2").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        when(service.get("1", 3L)).thenThrow(new UserNotFoundException());
        mvc.perform(get("/api/admin/users/3").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        when(service.changeRole("1", 1L, new ChangeUserRoleRequest(UserRole.USER)))
                .thenThrow(new CatalogException(HttpStatus.CONFLICT, "ADMIN_SELF_DEMOTION_NOT_ALLOWED", "자기 강등 금지"));
        mvc.perform(patch("/api/admin/users/1/role").with(jwt().jwt(token -> token.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_SELF_DEMOTION_NOT_ALLOWED"));
    }

    @Test
    void requiresAuthenticationForAllEndpoints() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users/2")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    private AdminUserResponse response() {
        return new AdminUserResponse(2L, "user@example.com", "사용자", AuthProvider.LOCAL,
                UserRole.ADMIN, LocalDateTime.of(2026, 9, 17, 0, 0), LocalDateTime.of(2026, 9, 17, 0, 0));
    }
}
