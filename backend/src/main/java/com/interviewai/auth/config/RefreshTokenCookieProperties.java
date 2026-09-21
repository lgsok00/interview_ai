package com.interviewai.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "auth.refresh-cookie")
public record RefreshTokenCookieProperties(
        @DefaultValue("refresh_token")
        String name,

        @DefaultValue("/api/auth")
        String path,

        @DefaultValue("Strict")
        String sameSite,

        @DefaultValue("false")
        boolean secure
) {

    public RefreshTokenCookieProperties {
        name = requireText(name, "Refresh Token cookie name");
        path = requireText(path, "Refresh Token cookie path");
        sameSite = requireText(sameSite, "Refresh Token cookie sameSite");

        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Refresh Token cookie path은 /로 시작해야 합니다.");
        }

        if (!sameSite.equals("Strict") && !sameSite.equals("Lax") && !sameSite.equals("None")) {
            throw new IllegalArgumentException("Refresh Token cookie SameSite는 Strict, Lax, None 중 하나여야 합니다.");
        }

        if (sameSite.equals("None") && !secure) {
            throw new IllegalArgumentException("SameSite=None인 Refresh Token cookie는 Secure가 필요합니다.");
        }
    }


    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "은 비어 있을 수 없습니다.");
        }

        return value.strip();
    }
}
