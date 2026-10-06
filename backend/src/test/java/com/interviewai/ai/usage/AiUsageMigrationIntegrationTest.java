package com.interviewai.ai.usage;

import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AiUsageMigrationIntegrationTest extends MySqlIntegrationTest {

    private static final String SUBJECT = "a".repeat(64);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 6);
    private static final LocalDateTime NOW = DAY.atStartOfDay();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void createsSubject() {
        jdbc.update("INSERT INTO ai_usage_subjects(subject_key, created_at) VALUES (?, ?)", SUBJECT, NOW);
    }

    @Test
    void appliesV21AndSeedsOnlyGlobalLockRow() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history WHERE version = '21' AND success = TRUE
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id FROM ai_usage_global_state", Integer.class))
                .containsExactly(1);
        assertCheckViolation(() -> jdbc.update("INSERT INTO ai_usage_global_state(id) VALUES (2)"),
                "chk_ai_usage_global_state_singleton");
    }

    @Test
    void separatesDailyFeatureCountersAndRetainsPreviousDay() {
        daily(SUBJECT, DAY, "INITIAL_QUESTIONS", 3);
        daily(SUBJECT, DAY, "FOLLOW_UP", 15);
        daily(SUBJECT, DAY.plusDays(1), "INITIAL_QUESTIONS", 0);
        assertThat(jdbc.queryForObject("""
                SELECT accepted FROM ai_usage_subject_daily
                WHERE subject_key = ? AND usage_date = ? AND feature = 'INITIAL_QUESTIONS'
                """, Integer.class, SUBJECT, DAY)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_usage_subject_daily", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void rejectsDuplicateDailyCounter() {
        daily(SUBJECT, DAY, "INITIAL_QUESTIONS", 0);
        assertThatThrownBy(() -> daily(SUBJECT, DAY, "INITIAL_QUESTIONS", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"subject", "global", "admin"})
    void rejectsNegativeCounter(String table) {
        String constraint = switch (table) {
            case "subject" -> "chk_ai_usage_subject_daily_count";
            case "global" -> "chk_ai_usage_global_daily_count";
            case "admin" -> "chk_ai_usage_admin_embedding_minutes_count";
            default -> throw new IllegalArgumentException(table);
        };
        assertCheckViolation(() -> {
            switch (table) {
                case "subject" -> daily(SUBJECT, DAY, "INITIAL_QUESTIONS", -1);
                case "global" -> jdbc.update("""
                        INSERT INTO ai_usage_global_daily(usage_date, chat_accepted) VALUES (?, -1)
                        """, DAY);
                case "admin" -> jdbc.update("""
                        INSERT INTO ai_usage_admin_embedding_minutes(subject_key, window_start, accepted)
                        VALUES (?, ?, -1)
                        """, SUBJECT, NOW);
                default -> throw new IllegalArgumentException(table);
            }
        }, constraint);
    }

    @Test
    void rejectsUnknownFeatureAndMissingSubject() {
        assertCheckViolation(() -> daily(SUBJECT, DAY, "UNKNOWN", 1),
                "chk_ai_usage_subject_daily_feature");
        assertThatThrownBy(() -> daily("b".repeat(64), DAY, "INITIAL_QUESTIONS", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preventsDuplicateActiveResourceAcrossUsers() {
        reservation("INITIAL_QUESTIONS", 42L, "ACTIVE", null, null, null);
        String anotherSubject = "b".repeat(64);
        jdbc.update("INSERT INTO ai_usage_subjects(subject_key, created_at) VALUES (?, ?)", anotherSubject, NOW);
        assertThatThrownBy(() -> insertReservation(anotherSubject,
                "INITIAL_QUESTIONS", 42L, "ACTIVE", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameNumericResourceInDifferentFeatures() {
        reservation("INITIAL_QUESTIONS", 42L, "ACTIVE", null, null, null);
        reservation("ANSWER_EVALUATION", 42L, "ACTIVE", null, null, null);
        assertThat(activeCount()).isEqualTo(2);
    }

    @Test
    void permitsNewAdmissionAfterPreviousReservationFinishes() {
        String previous = reservation("INITIAL_QUESTIONS", 42L, "ACTIVE", null, null, null);
        jdbc.update("UPDATE ai_usage_reservations SET status = 'FINISHED', finished_at = ? WHERE id = ?",
                NOW.plusSeconds(1), previous);
        String next = reservation("INITIAL_QUESTIONS", 42L, "ACTIVE", null, null, null);
        assertThat(next).isNotEqualTo(previous);
        assertThat(activeCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_usage_reservations", Integer.class)).isEqualTo(2);
    }

    @Test
    void allowsUnboundReservationsThenEnforcesUniqueResourceOnBinding() {
        String first = reservation("INITIAL_QUESTIONS", null, "ACTIVE", null, null, null);
        String second = reservation("INITIAL_QUESTIONS", null, "ACTIVE", null, null, null);
        jdbc.update("UPDATE ai_usage_reservations SET resource_id = 42 WHERE id = ?", first);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE ai_usage_reservations SET resource_id = 42 WHERE id = ?", second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void requiresLeaseForActiveSynchronousFollowUp() {
        assertCheckViolation(() -> reservation("FOLLOW_UP", 42L, "ACTIVE", null, null, null),
                "chk_ai_usage_reservations_follow_up_lease");
        reservation("FOLLOW_UP", 42L, "ACTIVE", NOW.plusSeconds(90), null, null);
        assertThat(activeCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FINISHED", "EXPIRED", "CANCELLED"})
    void requiresFinishTimeForEveryTerminalState(String status) {
        assertCheckViolation(() -> reservation("INITIAL_QUESTIONS", 42L, status, null, null, null),
                "chk_ai_usage_reservations_finished");
        reservation("INITIAL_QUESTIONS", 42L, status, null, NOW.plusSeconds(1), null);
        assertThat(activeCount()).isZero();
    }

    @Test
    void rejectsFinishTimeOnActiveReservation() {
        assertCheckViolation(() -> reservation("INITIAL_QUESTIONS", 42L, "ACTIVE", null, NOW, null),
                "chk_ai_usage_reservations_finished");
    }

    @Test
    void rejectsInvalidReservationFeatureStatusAndResource() {
        assertCheckViolation(() -> reservation("PERSONAL_EMBEDDING", 42L, "ACTIVE", null, null, null),
                "chk_ai_usage_reservations_feature");
        // Use a finish time so only the status constraint is violated.
        assertCheckViolation(() -> reservation("INITIAL_QUESTIONS", 42L, "UNKNOWN", null, NOW, null),
                "chk_ai_usage_reservations_status");
        assertCheckViolation(() -> reservation("INITIAL_QUESTIONS", 0L, "ACTIVE", null, null, null),
                "chk_ai_usage_reservations_resource");
    }

    @Test
    void accountDeletionRetainsDailyUsageAndReservationIdentity() {
        User user = users.saveAndFlush(User.createLocalUser(
                "usage@example.com", "encoded-password", "사용량 테스트"));
        daily(SUBJECT, DAY, "INITIAL_QUESTIONS", 3);
        String id = reservation("INITIAL_QUESTIONS", 42L, "ACTIVE", null, null, user.getId());
        users.delete(user);
        users.flush();
        assertThat(jdbc.queryForObject("SELECT user_id FROM ai_usage_reservations WHERE id = ?",
                Long.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT subject_key FROM ai_usage_reservations WHERE id = ?",
                String.class, id)).isEqualTo(SUBJECT);
        assertThat(jdbc.queryForObject("SELECT accepted FROM ai_usage_subject_daily WHERE subject_key = ?",
                Integer.class, SUBJECT)).isEqualTo(3);
        // SET NULL preserves the reservation; explicit cancellation must be wired in the next stage.
        assertThat(activeCount()).isEqualTo(1);
        User rejoined = users.saveAndFlush(User.createLocalUser(
                "usage@example.com", "encoded-password", "재가입 사용자"));
        assertThat(rejoined.getId()).isNotEqualTo(user.getId());
    }

    @Test
    void adminMinuteBucketIsUniqueAndRetainsEarlierWindow() {
        jdbc.update("""
                INSERT INTO ai_usage_admin_embedding_minutes(subject_key, window_start, accepted)
                VALUES (?, ?, 10), (?, ?, 0)
                """, SUBJECT, NOW, SUBJECT, NOW.plusMinutes(1));
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ai_usage_admin_embedding_minutes(subject_key, window_start, accepted)
                VALUES (?, ?, 0)
                """, SUBJECT, NOW)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_usage_admin_embedding_minutes", Integer.class))
                .isEqualTo(2);
    }

    private void assertCheckViolation(ThrowingCallable action, String constraint) {
        // MySQL error 3819 may be translated to UncategorizedSQLException (HY000).
        // Verify the rejected constraint instead of depending on Spring's exception subtype.
        assertThatThrownBy(action)
                .isInstanceOf(DataAccessException.class)
                .satisfies(exception -> {
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                    SQLException sql = (SQLException) exception.getCause();
                    assertThat(sql.getErrorCode()).isEqualTo(3819);
                    assertThat(sql.getMessage()).contains(constraint);
                });
    }

    private void daily(String subject, LocalDate day, String feature, int accepted) {
        jdbc.update("""
                INSERT INTO ai_usage_subject_daily(subject_key, usage_date, feature, accepted)
                VALUES (?, ?, ?, ?)
                """, subject, day, feature, accepted);
    }

    private String reservation(String feature, Long resourceId, String status,
                               LocalDateTime lease, LocalDateTime finished, Long userId) {
        return insertReservation(SUBJECT, feature, resourceId, status, lease, finished, userId);
    }

    private String insertReservation(String subject, String feature, Long resourceId, String status,
                                     LocalDateTime lease, LocalDateTime finished, Long userId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO ai_usage_reservations
                    (id, subject_key, user_id, feature, resource_id, status, lease_expires_at, created_at, finished_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, subject, userId, feature, resourceId, status, lease, NOW, finished);
        return id;
    }

    private Integer activeCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ai_usage_reservations WHERE status = 'ACTIVE'", Integer.class);
    }
}
