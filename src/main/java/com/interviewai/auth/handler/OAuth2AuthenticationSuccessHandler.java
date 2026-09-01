package com.interviewai.auth.handler;

import com.interviewai.auth.dto.LoginResponse;
import com.interviewai.auth.exception.InvalidOAuth2UserException;
import com.interviewai.auth.exception.OAuth2EmailConflictException;
import com.interviewai.auth.service.GithubOAuth2LoginService;
import com.interviewai.auth.service.GoogleOAuth2LoginService;
import com.interviewai.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String GOOGLE_REGISTRATION_ID = "google";
    private static final String GITHUB_REGISTRATION_ID = "github";
    private static final String VERIFIED_EMAIL_ATTRIBUTE = "verified_email";

    private final GoogleOAuth2LoginService googleOAuth2LoginService;
    private final GithubOAuth2LoginService githubOAuth2LoginService;
    private final ObjectMapper objectMapper;


    public OAuth2AuthenticationSuccessHandler(
            GoogleOAuth2LoginService googleOAuth2LoginService,
            GithubOAuth2LoginService githubOAuth2LoginService,
            ObjectMapper objectMapper
    ) {
        this.googleOAuth2LoginService = googleOAuth2LoginService;
        this.githubOAuth2LoginService = githubOAuth2LoginService;
        this.objectMapper = objectMapper;
    }


    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication authentication
    ) throws IOException {
        prepareResponse(response);

        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token)) {
            writeInvalidOAuth2User(response);

            return;
        }

        try {
            LoginResponse loginResponse = login(oauth2Token);

            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), loginResponse);

        } catch (OAuth2EmailConflictException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_CONFLICT,
                    "OAUTH2_EMAIL_CONFLICT",
                    exception.getMessage()
            );

        } catch (InvalidOAuth2UserException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_OAUTH2_USER",
                    exception.getMessage()
            );
        }
    }

    private LoginResponse login(OAuth2AuthenticationToken oauth2Token) {
        String registrationId = oauth2Token.getAuthorizedClientRegistrationId();

        return switch (registrationId) {
            case GOOGLE_REGISTRATION_ID -> loginWithGoogle(oauth2Token);
            case GITHUB_REGISTRATION_ID -> loginWithGithub(oauth2Token);
            default -> throw new InvalidOAuth2UserException();
        };
    }


    private LoginResponse loginWithGoogle(OAuth2AuthenticationToken oauth2Token) {
        if (!(oauth2Token.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new InvalidOAuth2UserException();
        }

        return googleOAuth2LoginService.login(oidcUser);
    }


    private LoginResponse loginWithGithub(OAuth2AuthenticationToken oauth2Token) {
        OAuth2User oauth2User = oauth2Token.getPrincipal();
        String verifiedEmail = oauth2User.getAttribute(VERIFIED_EMAIL_ATTRIBUTE);

        return githubOAuth2LoginService.login(oauth2User, verifiedEmail);
    }


    private void prepareResponse(HttpServletResponse response) {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }


    private void writeInvalidOAuth2User(HttpServletResponse response) throws IOException {
        writeError(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "INVALID_OAUTH2_USER",
                "OAuth2 사용자 정보가 올바르지 않습니다."
        );
    }


    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, message));
    }
}
