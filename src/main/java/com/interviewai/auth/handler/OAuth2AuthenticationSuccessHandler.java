package com.interviewai.auth.handler;

import com.interviewai.auth.dto.LoginResponse;
import com.interviewai.auth.exception.InvalidOAuth2UserException;
import com.interviewai.auth.exception.OAuth2EmailConflictException;
import com.interviewai.auth.service.GoogleOAuth2LoginService;
import com.interviewai.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String GOOGLE_REGISTRATION_ID = "google";

    private final GoogleOAuth2LoginService googleOAuth2LoginService;
    private final ObjectMapper objectMapper;


    public OAuth2AuthenticationSuccessHandler(GoogleOAuth2LoginService googleOAuth2LoginService, ObjectMapper objectMapper) {
        this.googleOAuth2LoginService = googleOAuth2LoginService;
        this.objectMapper = objectMapper;
    }


    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication authentication
    ) throws IOException {
        prepareResponse(response);

        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token)
                || !GOOGLE_REGISTRATION_ID.equals(oauth2Token.getAuthorizedClientRegistrationId())
                || !(oauth2Token.getPrincipal() instanceof OidcUser oidcUser)) {
            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_OAUTH2_USER",
                    "Google 사용자 정보가 올바르지 않습니다."
            );

            return;
        }

        try {
            LoginResponse loginResponse = googleOAuth2LoginService.login(oidcUser);

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


    private void prepareResponse(HttpServletResponse response) {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }


    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, message));
    }
}
