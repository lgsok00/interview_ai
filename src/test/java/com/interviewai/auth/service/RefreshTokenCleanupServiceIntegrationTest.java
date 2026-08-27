package com.interviewai.auth.service;

import com.interviewai.auth.entity.RefreshToken;
import com.interviewai.auth.repository.RefreshTokenRepository;
import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RefreshTokenCleanupServiceIntegrationTest extends MySqlIntegrationTest {

    private static final Instant EXPIRED_AT = Instant.parse("2026-08-28T00:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenCleanupService cleanupService;


    @BeforeEach
    void setUp() {
        cleanupService = new RefreshTokenCleanupService(refreshTokenRepository);
    }


    @Test
    @DisplayName("기준 시각 이전과 같은 시각에 만료된 토큰만 삭제한다")
    void deletesOnlyTokensExpiredAtOrBeforeReferenceTime() {
        User user = saveUser("user@example.com");
        saveToken(user, "a", EXPIRED_AT.minusSeconds(1));
        saveToken(user, "b", EXPIRED_AT);
        RefreshToken validToken = saveToken(user, "c", EXPIRED_AT.plusSeconds(1));
        refreshTokenRepository.flush();

        int deletedCount = cleanupService.deleteExpiredBatch(EXPIRED_AT, 10);
        refreshTokenRepository.flush();

        assertThat(deletedCount).isEqualTo(2);
        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .extracting(RefreshToken::getId)
                .isEqualTo(validToken.getId());
    }


    @Test
    @DisplayName("설정한 Batch 크기만큼 오래된 만료 토큰부터 나누어 삭제한다")
    void deletesExpiredTokensInBatches() {
        User user = saveUser("user@example.com");
        saveToken(user, "a", EXPIRED_AT.minusSeconds(3));
        saveToken(user, "b", EXPIRED_AT.minusSeconds(2));
        saveToken(user, "c", EXPIRED_AT.minusSeconds(1));
        RefreshToken validToken = saveToken(user, "d", EXPIRED_AT.plusSeconds(1));
        refreshTokenRepository.flush();

        int firstDeletedCount = cleanupService.deleteExpiredBatch(EXPIRED_AT, 2);
        int secondDeletedCount = cleanupService.deleteExpiredBatch(EXPIRED_AT, 2);
        int thirdDeletedCount = cleanupService.deleteExpiredBatch(EXPIRED_AT, 2);
        refreshTokenRepository.flush();

        assertThat(firstDeletedCount).isEqualTo(2);
        assertThat(secondDeletedCount).isEqualTo(1);
        assertThat(thirdDeletedCount).isZero();
        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .extracting(RefreshToken::getId)
                .isEqualTo(validToken.getId());
    }


    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.createLocalUser(
                email,
                "{bcrypt}encoded-password",
                "테스트유저"
        ));
    }


    private RefreshToken saveToken(User user, String tokenMarker, Instant expiresAt) {
        String tokenHash = tokenMarker.repeat(64);
        return refreshTokenRepository.save(RefreshToken.create(user, tokenHash, expiresAt));
    }
}
