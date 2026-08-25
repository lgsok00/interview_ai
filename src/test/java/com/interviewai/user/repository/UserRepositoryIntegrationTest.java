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
}
