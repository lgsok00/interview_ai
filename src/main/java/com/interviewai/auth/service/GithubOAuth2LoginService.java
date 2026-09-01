package com.interviewai.auth.service;

import com.interviewai.auth.dto.LoginResponse;
import com.interviewai.auth.exception.InvalidOAuth2UserException;
import com.interviewai.auth.exception.OAuth2EmailConflictException;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.repository.UserRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class GithubOAuth2LoginService {

    private static final int MAX_NICKNAME_LENGTH = 50;

    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;


    public GithubOAuth2LoginService(
            UserRepository userRepository, JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }


    @Transactional
    public LoginResponse login(OAuth2User oauth2User, String verifiedEmail) {
        if (oauth2User == null) {
            throw new InvalidOAuth2UserException();
        }

        String providerId = requireProviderId(oauth2User);
        String email = normalizeEmail(requireText(verifiedEmail));

        User user = userRepository
                .findByProviderAndProviderId(AuthProvider.GITHUB, providerId)
                .orElseGet(() -> createGithubUser(
                        email,
                        attributeText(oauth2User, "name"),
                        attributeText(oauth2User, "login"),
                        providerId
                ));

        JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issueAccessToken(user);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user);

        return LoginResponse.bearer(
                accessToken.token(),
                refreshToken.token(),
                accessToken.expiresIn(),
                refreshToken.expiresIn()
        );
    }


    private User createGithubUser(String email, String name, String login, String providerId) {
        if (userRepository.existsByEmail(email)) {
            throw new OAuth2EmailConflictException();
        }

        return userRepository
                .saveAndFlush(User.createGithubUser(email, resolveNickname(name, login, email), providerId));
    }


    private String resolveNickname(String name, String login, String email) {
        String nickname;

        if (hasText(name)) {
            nickname = name.trim();

        } else if (hasText(login)) {
            nickname = login.trim();

        } else {
            nickname = email.substring(0, email.indexOf('@'));
        }

        return nickname.substring(0, Math.min(nickname.length(), MAX_NICKNAME_LENGTH));
    }


    private String requireProviderId(OAuth2User oauth2User) {
        Object value = oauth2User.getAttribute("id");

        if (value == null) {
            throw new InvalidOAuth2UserException();
        }

        return requireText(value.toString());
    }


    private String attributeText(OAuth2User oauth2User, String attributeName) {
        Object value = oauth2User.getAttribute(attributeName);

        return value == null ? null : value.toString();
    }


    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }


    private String requireText(String value) {
        if (!hasText(value)) {
            throw new InvalidOAuth2UserException();
        }

        return value.trim();
    }


    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
