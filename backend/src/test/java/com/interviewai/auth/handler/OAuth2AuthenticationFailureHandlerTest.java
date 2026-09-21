package com.interviewai.auth.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.PRAGMA;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

class OAuth2AuthenticationFailureHandlerTest {

    private OAuth2AuthenticationFailureHandler failureHandler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;


    @BeforeEach
    void setUp() {
        failureHandler = new OAuth2AuthenticationFailureHandler(new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }


    @Test
    @DisplayName("OAuth2 인증 실패 시 일반화된 401 JSON 오류를 반환한다")
    void returnsGenericUnauthorizedResponse() throws Exception {
        failureHandler.onAuthenticationFailure(
                request,
                response,
                new BadCredentialsException("google access token: secret-value")
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(APPLICATION_JSON_VALUE);
        assertThat(response.getHeader(CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeader(PRAGMA)).isEqualTo("no-cache");
        assertThat(response.getContentAsString())
                .contains("OAUTH2_LOGIN_FAILED")
                .doesNotContain("secret-value")
                .doesNotContain("google access token");
    }
}
