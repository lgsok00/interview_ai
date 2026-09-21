package com.interviewai.auth.service;

import com.interviewai.auth.dto.GithubEmailResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GithubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String GITHUB_REGISTRATION_ID = "github";
    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";
    private static final String GITHUB_USER_NAME_ATTRIBUTE = "id";
    private static final String VERIFIED_EMAIL_ATTRIBUTE = "verified_email";

    private final DefaultOAuth2UserService delegate;
    private final RestClient restClient;


    public GithubOAuth2UserService(@Qualifier("githubRestClient") RestClient githubRestClient) {
        this.delegate = new DefaultOAuth2UserService();
        this.restClient = githubRestClient;
    }


    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);

        if (!isGithubRegistration(userRequest)) {
            return oauth2User;
        }

        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());

        validateGithubId(attributes);

        String verifiedEmail = findVerifiedEmail(userRequest.getAccessToken().getTokenValue());

        attributes.put(VERIFIED_EMAIL_ATTRIBUTE, verifiedEmail);


        return new DefaultOAuth2User(oauth2User.getAuthorities(), attributes, GITHUB_USER_NAME_ATTRIBUTE);
    }


    private boolean isGithubRegistration(OAuth2UserRequest userRequest) {
        return GITHUB_REGISTRATION_ID.equals(userRequest.getClientRegistration().getRegistrationId());
    }


    private void validateGithubId(Map<String, Object> attributes) {
        Object githubId = attributes.get(GITHUB_USER_NAME_ATTRIBUTE);

        if (githubId == null || githubId.toString().isBlank()) {
            throw invalidGithubUser();
        }
    }


    private String findVerifiedEmail(String accessToken) {
        List<GithubEmailResponse> emails = restClient.get()
                .uri(GITHUB_EMAILS_URL)
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    headers.set(
                            HttpHeaders.USER_AGENT,
                            "interviewai-ai-backend"
                    );
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        if (emails == null || emails.isEmpty()) {
            throw invalidGithubUser();
        }

        return emails.stream()
                .filter(GithubEmailResponse::verified)
                .filter(GithubEmailResponse::primary)
                .map(GithubEmailResponse::email)
                .filter(this::hasText)
                .findFirst()
                .orElseGet(() -> emails.stream()
                        .filter(GithubEmailResponse::verified)
                        .map(GithubEmailResponse::email)
                        .filter(this::hasText)
                        .findFirst()
                        .orElseThrow(this::invalidGithubUser)
                );
    }


    private OAuth2AuthenticationException invalidGithubUser() {
        OAuth2Error error = new OAuth2Error(
                "invalid_github_user",
                "검증된 Github 이메일을 찾을 수 없습니다.",
                null
        );

        return new OAuth2AuthenticationException(error);
    }


    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
