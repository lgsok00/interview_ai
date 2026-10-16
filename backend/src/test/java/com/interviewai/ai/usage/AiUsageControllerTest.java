package com.interviewai.ai.usage;

import com.interviewai.auth.filter.UserStatusAuthenticationFilter;
import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiUsageController.class)
@Import({SecurityConfig.class, UserStatusAuthenticationFilter.class})
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
class AiUsageControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @MockitoBean
    private AiUsageQueryService service;
    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private OAuth2AuthenticationSuccessHandler successHandler;
    @MockitoBean
    private OAuth2AuthenticationFailureHandler failureHandler;
    @MockitoBean
    private GithubOAuth2UserService githubOAuth2UserService;

    @Test
    void returnsPersonalContractWithNoStoreAndIgnoresRequestedUserId() throws Exception {
        when(users.findById(17L)).thenReturn(Optional.of(
                User.createLocalUser("user@example.com", "hash", "사용자")));
        when(service.getUsage("17")).thenReturn(response(true));
        mockMvc.perform(get("/api/ai/usage").param("userId", "999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("17")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.chatAdmissionEnabled").value(true))
                .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.usageDate").value("2026-10-16"))
                .andExpect(jsonPath("$.resetAt").value("2026-10-17T00:00:00+09:00"))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.activeLimit").value(2))
                .andExpect(jsonPath("$.features[0].feature").value("INITIAL_QUESTIONS"))
                .andExpect(jsonPath("$.features[0].limit").value(3))
                .andExpect(jsonPath("$.features[0].used").value(1))
                .andExpect(jsonPath("$.features[0].remaining").value(2))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.subjectKey").doesNotExist())
                .andExpect(jsonPath("$.globalUsed").doesNotExist())
                .andExpect(jsonPath("$", aMapWithSize(8)));
        verify(service).getUsage("17");
        verifyNoMoreInteractions(service);
    }

    @Test
    void disabledResponseDoesNotPretendCountersWereMeasured() throws Exception {
        when(service.getUsage("17")).thenReturn(response(false));
        mockMvc.perform(get("/api/ai/usage").with(jwt().jwt(token -> token.subject("17"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.active").value(nullValue()))
                .andExpect(jsonPath("$.activeLimit").value(nullValue()))
                .andExpect(jsonPath("$.features").isEmpty());
    }

    @Test
    void anonymousRequestIsRejectedBeforeService() throws Exception {
        mockMvc.perform(get("/api/ai/usage")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void suspendedBearerUserIsRejectedBeforeService() throws Exception {
        var user = User.createLocalUser("user@example.com", "hash", "사용자");
        user.suspend(LocalDateTime.of(2026, 10, 16, 0, 0));
        when(users.findById(17L)).thenReturn(Optional.of(user));
        mockMvc.perform(get("/api/ai/usage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("17")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_SUSPENDED"));
        verifyNoInteractions(service);
    }

    @Test
    void deletedBearerUserIsRejectedBeforeService() throws Exception {
        when(users.findById(17L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/ai/usage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("17")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "9223372036854775808"})
    void malformedBearerSubjectIsRejectedBeforeService(String subject) throws Exception {
        mockMvc.perform(get("/api/ai/usage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(subject)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        verifyNoInteractions(service);
    }

    @Test
    void usageFailureUsesStandard503WithoutPrivateCause() throws Exception {
        when(service.getUsage("17")).thenThrow(AiUsageException.unavailable(
                new IllegalStateException("private-database-detail")));
        mockMvc.perform(get("/api/ai/usage").with(jwt().jwt(token -> token.subject("17"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_USAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.errors").isMap())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(content().string(not(containsString("private-database-detail"))));
    }

    private String accessToken(String subject) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("interview-ai")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private AiUsageResponse response(boolean enabled) {
        return new AiUsageResponse(enabled, true, "Asia/Seoul", LocalDate.of(2026, 10, 16),
                OffsetDateTime.parse("2026-10-17T00:00:00+09:00"),
                enabled ? 1L : null, enabled ? 2 : null,
                enabled ? List.of(new AiUsageResponse.FeatureUsage(
                        AiUsageFeature.INITIAL_QUESTIONS, 3, 1, 2)) : List.of());
    }
}
