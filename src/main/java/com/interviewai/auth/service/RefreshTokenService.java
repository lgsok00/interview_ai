package com.interviewai.auth.service;

import com.interviewai.auth.entity.RefreshToken;
import com.interviewai.auth.exception.InvalidRefreshTokenException;
import com.interviewai.auth.repository.RefreshTokenRepository;
import com.interviewai.global.config.JwtProperties;
import com.interviewai.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom;


    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.secureRandom = new SecureRandom();
    }


    @Transactional
    public IssuedRefreshToken issue(User user) {
        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(jwtProperties.refreshTokenExpiration());

        RefreshToken refreshToken = RefreshToken.create(user, hash(rawToken), expiresAt);

        refreshTokenRepository.save(refreshToken);

        return new IssuedRefreshToken(rawToken, jwtProperties.refreshTokenExpiration().toSeconds());
    }


    @Transactional
    public RotatedRefreshToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        String tokenHash = hash(rawToken);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (refreshToken.isExpired(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        String newRawToken = generateToken();
        Instant newExpiresAt = Instant.now().plus(jwtProperties.refreshTokenExpiration());

        refreshToken.rotate(hash(newRawToken), newExpiresAt);

        return new RotatedRefreshToken(
                refreshToken.getUser(),
                newRawToken,
                jwtProperties.refreshTokenExpiration().toSeconds()
        );
    }


    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.deleteByTokenHash(hash(rawToken));
    }


    @Transactional
    public int revokeAll(Long userId) {
        return refreshTokenRepository.deleteAllByUserId(userId);
    }


    private String generateToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }


    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hashedBytes);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }


    public record IssuedRefreshToken(String token, long expiresIn) {
    }


    public record RotatedRefreshToken(User user, String token, long expiresIn) {
    }
}
