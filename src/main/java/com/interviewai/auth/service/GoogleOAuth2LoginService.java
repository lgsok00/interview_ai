package com.interviewai.auth.service;

import com.interviewai.auth.dto.LoginResponse;
import com.interviewai.auth.exception.InvalidOAuth2UserException;
import com.interviewai.auth.exception.OAuth2EmailConflictException;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.repository.UserRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class GoogleOAuth2LoginService {

    private static final int MAX_NICKNAME_LENGTH = 50;

    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;


    public GoogleOAuth2LoginService(
            UserRepository userRepository, JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }


    @Transactional
    public LoginResponse login(OidcUser oidcUser) {
        if (oidcUser == null || !Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw new InvalidOAuth2UserException();
        }

        String providerId = requireText(oidcUser.getSubject());
        String email = normalizeEmail(requireText(oidcUser.getEmail()));

        User user = userRepository
                .findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
                .orElseGet(() -> createGoogleUser(email, oidcUser.getFullName(), providerId));

        JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issueAccessToken(user);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user);

        return LoginResponse.bearer(
                accessToken.token(),
                refreshToken.token(),
                accessToken.expiresIn(),
                refreshToken.expiresIn()
        );
    }


    private User createGoogleUser(String email, String name, String providerId) {
        if (userRepository.existsByEmail(email)) {
            throw new OAuth2EmailConflictException();
        }

        return userRepository.saveAndFlush(
                User.createGoogleUser(email, resolveNickname(name, email), providerId)
        );
    }


    private String resolveNickname(String name, String email) {
        String nickname = name == null || name.isBlank()
                ? email.substring(0, email.indexOf('@'))
                : name.trim();

        return nickname.substring(0, Math.min(nickname.length(), MAX_NICKNAME_LENGTH));
    }


    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }


    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidOAuth2UserException();
        }

        return value.trim();
    }
}
