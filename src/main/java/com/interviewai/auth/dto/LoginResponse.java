package com.interviewai.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshTokenExpiresIn
) {

    public static LoginResponse bearer(
            String accessToken,
            String refreshToken,
            long expiresIn,
            long refreshTokenExpiresIn
    ) {
        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresIn,
                refreshTokenExpiresIn
        );
    }
}
