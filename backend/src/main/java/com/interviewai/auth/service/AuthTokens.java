package com.interviewai.auth.service;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn
) {

    public AuthTokens {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access Token은 필수입니다.");
        }

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh Token은 필수입니다.");
        }

        if (accessTokenExpiresIn <= 0 || refreshTokenExpiresIn <= 0) {
            throw new IllegalArgumentException("토큰 만료 시간은 양수여야 합니다.");
        }
    }
}
