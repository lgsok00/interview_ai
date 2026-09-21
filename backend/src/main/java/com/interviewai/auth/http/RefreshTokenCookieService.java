package com.interviewai.auth.http;

import com.interviewai.auth.config.RefreshTokenCookieProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookieService {

    private final RefreshTokenCookieProperties properties;


    public RefreshTokenCookieService(RefreshTokenCookieProperties properties) {
        this.properties = properties;
    }


    public String cookieName() {
        return properties.name();
    }


    public void write(HttpServletResponse response, String refreshToken, long expiresInSeconds) {
        ResponseCookie cookie = ResponseCookie
                .from(properties.name(), refreshToken)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path())
                .maxAge(Duration.ofSeconds(expiresInSeconds))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }


    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from(properties.name(), "")
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path())
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
