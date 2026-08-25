package com.interviewai.auth.service;

import com.interviewai.auth.entity.RefreshToken;
import com.interviewai.auth.exception.InvalidRefreshTokenException;
import com.interviewai.auth.repository.RefreshTokenRepository;
import com.interviewai.global.config.JwtProperties;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static com.interviewai.support.AuthFixtures.localUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;


    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                SECRET,
                Duration.ofHours(1),
                Duration.ofDays(14)
        );

        refreshTokenService = new RefreshTokenService(refreshTokenRepository, properties);
    }


    @Test
    @DisplayName("Refresh Token 원문을 반환하고 해시만 저장한다")
    void issuesRefreshTokenAndStoresOnlyHash() {
        User user = localUser();
        Instant beforeIssue = Instant.now();

        RefreshTokenService.IssuedRefreshToken issuedToken = refreshTokenService.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken savedToken = captor.getValue();

        assertThat(issuedToken.token()).isNotBlank();
        assertThat(issuedToken.expiresIn()).isEqualTo(1209600);
        assertThat(savedToken.getUser()).isSameAs(user);
        assertThat(savedToken.getTokenHash())
                .isEqualTo(hash(issuedToken.token()))
                .isNotEqualTo(issuedToken.token());
        assertThat(savedToken.getExpiresAt())
                .isAfterOrEqualTo(beforeIssue.plus(Duration.ofDays(14)));
    }


    @Test
    @DisplayName("유효한 Refresh Token을 새로운 토큰으로 회전한다")
    void rotatesValidRefreshToken() {
        User user = localUser();
        String oldRawToken = "old-refresh-token";

        RefreshToken savedToken = RefreshToken.create(
                user,
                hash(oldRawToken),
                Instant.now().plus(Duration.ofDays(1))
        );

        when(refreshTokenRepository.findByTokenHashForUpdate(hash(oldRawToken))).thenReturn(Optional.of(savedToken));

        RefreshTokenService.RotatedRefreshToken rotatedToken = refreshTokenService.rotate(oldRawToken);

        assertThat(rotatedToken.user()).isSameAs(user);
        assertThat(rotatedToken.token())
                .isNotBlank()
                .isNotEqualTo(oldRawToken);
        assertThat(rotatedToken.expiresIn()).isEqualTo(1209600);
        assertThat(savedToken.getTokenHash())
                .isEqualTo(hash(rotatedToken.token()))
                .isNotEqualTo(hash(oldRawToken));
    }


    @Test
    @DisplayName("존재하지 않는 Refresh Token이면 실패한다")
    void rejectsUnknownRefreshToken() {
        String rawToken = "unknown-refresh-token";

        when(refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken)).isInstanceOf(InvalidRefreshTokenException.class);
    }


    @Test
    @DisplayName("만료된 Refresh Token이면 실패한다")
    void rejectsExpiredRefreshToken() {
        User user = localUser();
        String rawToken = "expired-refresh-token";

        RefreshToken expiredToken = RefreshToken.create(
                user,
                hash(rawToken),
                Instant.now().minusSeconds(1)
        );

        when(refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(expiredToken.getTokenHash()).isEqualTo(hash(rawToken));
    }


    @Test
    @DisplayName("Refresh Token이 비어 있으면 조회하지 않고 실패한다")
    void rejectsBlankRefreshToken() {
        assertThatThrownBy(() -> refreshTokenService.rotate(" "))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(refreshTokenRepository);
    }


    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));

        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}