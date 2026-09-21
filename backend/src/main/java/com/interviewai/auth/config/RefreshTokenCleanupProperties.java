package com.interviewai.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth.refresh-token.cleanup")
public record RefreshTokenCleanupProperties(
        boolean enabled,
        Duration fixedDelay,
        int batchSize
) {

    public RefreshTokenCleanupProperties {
        if (fixedDelay == null || fixedDelay.isZero() || fixedDelay.isNegative()) {
            throw new IllegalArgumentException("Refresh Token 정리 주기는 0보다 커야 합니다.");
        }

        if (batchSize <= 0) {
            throw new IllegalArgumentException("Refresh Token 정리 Batch 크기는 0보다 커야 합니다.");
        }
    }
}
