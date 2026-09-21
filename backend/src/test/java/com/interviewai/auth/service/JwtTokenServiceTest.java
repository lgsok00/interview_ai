package com.interviewai.auth.service;

import com.interviewai.global.config.JwtProperties;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenServiceTest {

    private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";

    private JwtTokenService jwtTokenService;
    private JwtDecoder jwtDecoder;


    @BeforeEach
    void setUp() {
        SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtProperties properties = new JwtProperties(SECRET, Duration.ofHours(1), Duration.ofDays(14));

        jwtTokenService = new JwtTokenService(NimbusJwtEncoder.withSecretKey(secretKey).build(), properties);

        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
    }


    @Test
    @DisplayName("사용자 정보가 포함된 JWT를 발급한다")
    void issuesJwtContainingUserInformation() {
        User user = mock(User.class);

        when(user.getId()).thenReturn(42L);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getRole()).thenReturn(UserRole.USER);

        JwtTokenService.IssuedAccessToken issuedToken = jwtTokenService.issueAccessToken(user);

        Jwt jwt = jwtDecoder.decode(issuedToken.token());

        Instant issuedAt = Objects.requireNonNull(jwt.getIssuedAt());
        Instant expiresAt = Objects.requireNonNull(jwt.getExpiresAt());

        assertThat(issuedToken.expiresIn()).isEqualTo(3600);
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("user@example.com");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
        assertThat(Duration.between(issuedAt, expiresAt)).isEqualTo(Duration.ofHours(1));
    }
}
