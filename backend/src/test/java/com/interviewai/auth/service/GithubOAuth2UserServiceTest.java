package com.interviewai.auth.service;

import com.interviewai.auth.dto.GithubEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GithubOAuth2UserServiceTest {

    private DefaultOAuth2UserService delegate;
    private RestClient restClient;
    private TestRequestSpec requestSpec;
    private RestClient.ResponseSpec responseSpec;
    private OAuth2UserRequest userRequest;
    private GithubOAuth2UserService githubOAuth2UserService;


    @BeforeEach
    void setUp() {
        delegate = mock(DefaultOAuth2UserService.class);
        restClient = mock(RestClient.class);
        requestSpec = mock(TestRequestSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        userRequest = mock(OAuth2UserRequest.class, RETURNS_DEEP_STUBS);
        githubOAuth2UserService = new GithubOAuth2UserService(restClient);
        ReflectionTestUtils.setField(githubOAuth2UserService, "delegate", delegate);

        when(userRequest.getClientRegistration().getRegistrationId())
                .thenReturn("github");
        when(userRequest.getAccessToken().getTokenValue())
                .thenReturn("github-access-token");
    }


    @Test
    @DisplayName("검증된 기본 GitHub 이메일을 사용자 속성에 추가한다")
    void addsVerifiedPrimaryEmailToAttributes() {
        OAuth2User oauth2User = githubUser(Map.of(
                "id", 12345678L,
                "login", "github-user"
        ));
        when(delegate.loadUser(userRequest)).thenReturn(oauth2User);
        stubEmailResponse(List.of(
                new GithubEmailResponse("secondary@example.com", false, true),
                new GithubEmailResponse("primary@example.com", true, true)
        ));

        OAuth2User result = Objects.requireNonNull(githubOAuth2UserService.loadUser(userRequest));

        assertThat(result.getName()).isEqualTo("12345678");
        assertThat(result.<String>getAttribute("verified_email"))
                .isEqualTo("primary@example.com");
        assertThat(result.<String>getAttribute("login"))
                .isEqualTo("github-user");
    }


    @Test
    @DisplayName("검증된 기본 이메일이 없으면 첫 번째 검증 이메일을 사용한다")
    void usesFirstVerifiedEmailWhenPrimaryIsUnavailable() {
        when(delegate.loadUser(userRequest)).thenReturn(githubUser(Map.of("id", 12345678L)));
        stubEmailResponse(List.of(
                new GithubEmailResponse("unverified@example.com", true, false),
                new GithubEmailResponse("verified@example.com", false, true)
        ));

        OAuth2User result = Objects.requireNonNull(githubOAuth2UserService.loadUser(userRequest));

        assertThat(result.<String>getAttribute("verified_email"))
                .isEqualTo("verified@example.com");
    }


    @Test
    @DisplayName("검증된 GitHub 이메일이 없으면 인증을 거부한다")
    void rejectsUserWithoutVerifiedEmail() {
        when(delegate.loadUser(userRequest)).thenReturn(githubUser(Map.of("id", 12345678L)));
        stubEmailResponse(List.of(
                new GithubEmailResponse("unverified@example.com", true, false),
                new GithubEmailResponse(" ", false, true)
        ));

        assertThatThrownBy(() -> githubOAuth2UserService.loadUser(userRequest))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("검증된 Github 이메일");
    }


    @Test
    @DisplayName("GitHub provider id가 없으면 이메일 API를 호출하지 않고 거부한다")
    void rejectsMissingGithubIdBeforeRequestingEmails() {
        when(delegate.loadUser(userRequest)).thenReturn(githubUser(Map.of("login", "github-user")));

        assertThatThrownBy(() -> githubOAuth2UserService.loadUser(userRequest))
                .isInstanceOf(OAuth2AuthenticationException.class);

        verifyNoInteractions(restClient);
    }


    @Test
    @DisplayName("GitHub 이외 registration은 기본 사용자 정보를 그대로 반환한다")
    void returnsDelegatedUserForOtherRegistration() {
        OAuth2User oauth2User = githubUser(Map.of("id", "other-id"));
        when(userRequest.getClientRegistration().getRegistrationId())
                .thenReturn("other");
        when(delegate.loadUser(userRequest)).thenReturn(oauth2User);

        OAuth2User result = githubOAuth2UserService.loadUser(userRequest);

        assertThat(result).isSameAs(oauth2User);
        verifyNoInteractions(restClient);
    }


    private void stubEmailResponse(List<GithubEmailResponse> emails) {
        doReturn(requestSpec)
                .when(restClient)
                .get();

        when(requestSpec.uri("https://api.github.com/user/emails"))
                .thenReturn(requestSpec);
        when(requestSpec.headers(any())).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ArgumentMatchers
                .<ParameterizedTypeReference<List<GithubEmailResponse>>>any()
        )).thenReturn(emails);
    }


    private OAuth2User githubUser(Map<String, Object> attributes) {
        String nameAttributeKey = attributes.containsKey("id") ? "id" : "login";

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                nameAttributeKey
        );
    }


    private interface TestRequestSpec extends RestClient.RequestHeadersUriSpec<TestRequestSpec> {
    }
}
