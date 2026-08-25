package com.interviewai.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenExpiration,
        Duration refreshTokenExpiration
) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret은 32바이트 이상이어야 합니다.");
        }

        validateExpiration(accessTokenExpiration, "Access Token");
        validateExpiration(refreshTokenExpiration, "Refresh Token");
    }

    private static void validateExpiration(Duration expiration, String tokenName) {
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException(tokenName + " 만료 시간은 0보다 커야 합니다.");
        }
    }
}
