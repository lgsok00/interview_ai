package com.interviewai.auth.service;

import com.interviewai.auth.exception.InvalidRefreshTokenException;
import com.interviewai.auth.repository.RefreshTokenRepository;
import com.interviewai.global.config.JwtProperties;
import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class RefreshTokenServiceIntegrationTest extends MySqlIntegrationTest {

    private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";

    @Autowired
    private UserRepository userRepository;

    @Autowired
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
    @DisplayName("저장된 Refresh Token을 로그아웃하면 DB에서 삭제한다")
    void deletesStoredRefreshTokenOnLogout() {
        User user = saveUser("user@example.com");
        RefreshTokenService.IssuedRefreshToken issuedToken = refreshTokenService.issue(user);

        refreshTokenRepository.flush();

        assertThat(refreshTokenRepository.count()).isEqualTo(1);

        refreshTokenService.revoke(issuedToken.token());
        refreshTokenRepository.flush();

        assertThat(refreshTokenRepository.count()).isZero();
    }


    @Test
    @DisplayName("존재하지 않는 Refresh Token을 폐기해도 예외 없이 완료한다")
    void ignoresUnknownRefreshTokenOnLogout() {
        assertThatCode(() -> refreshTokenService.revoke("unknown-refresh-token"))
                .doesNotThrowAnyException();

        assertThat(refreshTokenRepository.count()).isZero();
    }


    @Test
    @DisplayName("폐기한 Refresh Token으로 재발급할 수 없다")
    void rejectsRefreshWithRevokedToken() {
        User user = saveUser("user@example.com");
        RefreshTokenService.IssuedRefreshToken issuedToken = refreshTokenService.issue(user);

        refreshTokenService.revoke(issuedToken.token());

        assertThatThrownBy(() -> refreshTokenService.rotate(issuedToken.token()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }


    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.createLocalUser(
                email,
                "{bcrypt}encoded-password",
                "테스트유저"
        ));
    }
}
