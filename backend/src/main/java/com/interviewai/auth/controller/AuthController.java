package com.interviewai.auth.controller;

import com.interviewai.auth.dto.LoginRequest;
import com.interviewai.auth.dto.LoginResponse;
import com.interviewai.auth.dto.SignupRequest;
import com.interviewai.auth.dto.SignupResponse;
import com.interviewai.auth.http.RefreshTokenCookieService;
import com.interviewai.auth.service.AuthService;
import com.interviewai.auth.service.AuthTokens;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieService refreshTokenCookies;


    public AuthController(AuthService authService, RefreshTokenCookieService refreshTokenCookies) {
        this.authService = authService;
        this.refreshTokenCookies = refreshTokenCookies;
    }


    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }


    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthTokens tokens = authService.login(request);

        refreshTokenCookies.write(response, tokens.refreshToken(), tokens.refreshTokenExpiresIn());

        return LoginResponse.bearer(tokens.accessToken(), tokens.accessTokenExpiresIn());
    }


    @PostMapping("/refresh")
    public LoginResponse refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authService.refresh(refreshToken);

        refreshTokenCookies.write(response, tokens.refreshToken(), tokens.refreshTokenExpiresIn());

        return LoginResponse.bearer(tokens.accessToken(), tokens.accessTokenExpiresIn());
    }


    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken);
        refreshTokenCookies.clear(response);
    }


    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        authService.logoutAll(jwt.getSubject());
        refreshTokenCookies.clear(response);
    }
}
