package com.interviewai.user.repository;

import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryIntegrationTest extends MySqlIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    @Test
    @DisplayName("Flyway V1 migration이 적용된다")
    void appliesFlywayMigration() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version = '1' AND success = TRUE
                        """,
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }


    @Test
    @DisplayName("로컬 사용자를 저장하고 이메일로 조회한다")
    void savesAndFindsLocalUser() {
        User user = User.createLocalUser(
                "user@example.com",
                "{bcrypt}encoded-password",
                "테스트유저"
        );

        User savedUser = userRepository.saveAndFlush(user);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(userRepository.findByEmail("user@example.com"))
                .contains(savedUser);
    }


    @Test
    @DisplayName("동일한 이메일은 DB unique constraint로 거부한다")
    void rejectsDuplicateEmail() {
        userRepository.saveAndFlush(User.createLocalUser(
                "user@example.com",
                "{bcrypt}first-password",
                "첫 번째 사용자"
        ));

        User duplicate = User.createLocalUser(
                "user@example.com",
                "{bcrypt}second-password",
                "두 번째 사용자"
        );

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }


    @Test
    @DisplayName("사용자를 삭제하면 해당 사용자의 모든 Refresh Token도 삭제한다")
    void deletesRefreshTokensWhenUserIsDeleted() {
        User user = userRepository.saveAndFlush(User.createLocalUser(
                "user@example.com",
                "{bcrypt}encoded-password",
                "테스트유저"
        ));

        jdbcTemplate.update(
                """
                        INSERT INTO refresh_tokens
                            (user_id, token_hash, expires_at, created_at, updated_at)
                        VALUES
                            (?, ?, DATE_ADD(NOW(6), INTERVAL 14 DAY), NOW(6), NOW(6)),
                            (?, ?, DATE_ADD(NOW(6), INTERVAL 14 DAY), NOW(6), NOW(6))
                        """,
                user.getId(), "a".repeat(64),
                user.getId(), "b".repeat(64)
        );

        userRepository.delete(user);
        userRepository.flush();

        Integer tokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?",
                Integer.class,
                user.getId()
        );

        assertThat(tokenCount).isZero();
    }


    @Test
    @DisplayName("탈퇴한 OAuth2 사용자는 같은 이메일과 provider 계정으로 재가입할 수 있다")
    void allowsOAuth2UserToRejoinAfterDeletion() {
        User deletedUser = userRepository.saveAndFlush(User.createGoogleUser(
                "oauth@example.com",
                "OAuth 사용자",
                "google-provider-id"
        ));
        Long deletedUserId = deletedUser.getId();

        userRepository.delete(deletedUser);
        userRepository.flush();

        User rejoinedUser = userRepository.saveAndFlush(User.createGoogleUser(
                "oauth@example.com",
                "재가입 사용자",
                "google-provider-id"
        ));

        assertThat(rejoinedUser.getId()).isNotEqualTo(deletedUserId);
        assertThat(userRepository.findByEmail("oauth@example.com"))
                .contains(rejoinedUser);
    }
}
