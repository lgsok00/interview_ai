package com.interviewai.ai.usage;

import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({AiUsageQueryRepository.class, AiUsageQueryService.class, AiUsageIdentity.class,
        AiUsageQueryIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AiUsageQueryIntegrationTest extends MySqlIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 16);
    private static final LocalDateTime NOW = DAY.atTime(10, 0);
    private static final List<String> TABLES = List.of(
            "users", "ai_usage_global_state", "ai_usage_subjects", "ai_usage_subject_daily",
            "ai_usage_global_daily", "ai_usage_reservations", "ai_usage_admin_embedding_minutes");

    @Autowired
    private AiUsageQueryService service;
    @Autowired
    private AiUsageIdentity identity;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactions;
    @MockitoSpyBean
    private AiUsageQueryRepository repository;

    private AiUsageQueryRepository clockTarget;
    private long userId;
    private String subject;
    private long resourceId;

    @BeforeEach
    void setUp() {
        // Testcontainers 전용 DB의 데이터를 매 테스트 전에 초기화한다.
        // noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM ai_usage_reservations");
        // noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM ai_usage_subject_daily");
        // noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM ai_usage_global_daily");
        // noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM ai_usage_admin_embedding_minutes");
        // noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM ai_usage_subjects");
        // noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM users");
        clockTarget = AopTestUtils.getUltimateTargetObject(repository);
        // MANDATORY 프록시를 트랜잭션 밖에서 호출하지 않도록 실제 spy target에 clock만 설정한다.
        doReturn(NOW).when(clockTarget).currentUtcTime();
        userId = createUser();
        subject = identity.subjectKey("user@example.com");
    }

    @Test
    void unusedSubjectReturnsZeroWithoutCreatingRows() {
        var before = databaseState();
        var result = query();
        assertThat(result.features()).hasSize(4).allSatisfy(item -> {
            assertThat(item.used()).isZero();
            assertThat(item.remaining()).isEqualTo(item.limit());
        });
        assertThat(result.active()).isZero();
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void isolatesDateFeatureAndSubjectAndExcludesEmbedding() {
        addDaily(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS, 2);
        addDaily(subject, DAY, AiUsageFeature.FOLLOW_UP, 5);
        addDaily(subject, DAY, AiUsageFeature.ANSWER_EVALUATION, 9);
        addDaily(subject, DAY, AiUsageFeature.COVER_LETTER_DRAFT, 1);
        addDaily(subject, DAY, AiUsageFeature.PERSONAL_EMBEDDING, 20);
        addDaily(subject, DAY.minusDays(1), AiUsageFeature.INITIAL_QUESTIONS, 3);
        addDaily(identity.subjectKey("other@example.com"), DAY, AiUsageFeature.INITIAL_QUESTIONS, 3);
        var before = databaseState();

        assertThat(query().features()).containsExactly(
                new AiUsageResponse.FeatureUsage(AiUsageFeature.INITIAL_QUESTIONS, 3, 2, 1),
                new AiUsageResponse.FeatureUsage(AiUsageFeature.FOLLOW_UP, 15, 5, 10),
                new AiUsageResponse.FeatureUsage(AiUsageFeature.ANSWER_EVALUATION, 30, 9, 21),
                new AiUsageResponse.FeatureUsage(AiUsageFeature.COVER_LETTER_DRAFT, 3, 1, 2));
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void missingDailyRowsStillIncludePriorDayActiveReservationsAndExpiredLeases() {
        addReservation(subject, "INITIAL_QUESTIONS", "ACTIVE", NOW.minusDays(1));
        addReservation(subject, "FOLLOW_UP", "ACTIVE", NOW.minusDays(1));
        for (String state : List.of("FINISHED", "EXPIRED", "CANCELLED")) {
            addReservation(subject, "ANSWER_EVALUATION", state, NOW);
        }
        addReservation(identity.subjectKey("other@example.com"), "INITIAL_QUESTIONS", "ACTIVE", NOW);
        var before = databaseState();

        assertThat(query().active()).isEqualTo(2L);
        assertThat(query().features()).allSatisfy(item -> assertThat(item.used()).isZero());
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void databaseUtcClockSwitchesKstDayWithoutResettingActiveSlots() {
        addDaily(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS, 3);
        addDaily(subject, DAY.plusDays(1), AiUsageFeature.INITIAL_QUESTIONS, 1);
        addReservation(subject, "INITIAL_QUESTIONS", "ACTIVE", NOW);
        doReturn(DAY.atTime(14, 59, 59, 999999000)).when(clockTarget).currentUtcTime();
        assertThat(query().features().getFirst().used()).isEqualTo(3);

        doReturn(DAY.atTime(15, 0)).when(clockTarget).currentUtcTime();
        var nextDay = query();
        assertThat(nextDay.usageDate()).isEqualTo(DAY.plusDays(1));
        assertThat(nextDay.features().getFirst().used()).isEqualTo(1);
        assertThat(nextDay.active()).isEqualTo(1L);
        assertThat(nextDay.resetAt().toLocalDate()).isEqualTo(DAY.plusDays(2));
    }

    @Test
    void countersSurviveDeletionAndRejoiningWithNormalizedEmail() {
        addDaily(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS, 2);
        users.deleteById(userId);
        userId = createUser();
        assertThat(identity.subjectKey("  USER@EXAMPLE.COM  ")).isEqualTo(subject);
        assertThat(query().features().getFirst().used()).isEqualTo(2);
    }

    @Test
    void retainsAcceptedCountsAboveConfiguredLimitWithoutNegativeRemaining() {
        addDaily(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS, 8);
        var usage = query().features().getFirst();
        assertThat(usage.used()).isEqualTo(8);
        assertThat(usage.limit()).isEqualTo(3);
        assertThat(usage.remaining()).isZero();
    }

    @Test
    void globalCountersDoNotLeakIntoPersonalUsage() {
        jdbc.update("INSERT INTO ai_usage_global_daily (usage_date, chat_accepted) VALUES (?, 500)",
                java.sql.Date.valueOf(DAY));
        var before = databaseState();
        assertThat(query().features()).allSatisfy(item -> assertThat(item.used()).isZero());
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void queryServiceStartsReadOnlyRepeatableReadTransaction() {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
            return NOW;
        }).when(clockTarget).currentUtcTime();
        query();
    }

    @Test
    void repositoryRequiresTransactionAndRealDatabaseClockIsUtc() {
        assertThatThrownBy(() -> repository.findSnapshot(subject, DAY))
                .isInstanceOf(IllegalTransactionStateException.class);
        doCallRealMethod().when(clockTarget).currentUtcTime();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var lower = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",
                    (rs, row) -> rs.getObject(1, LocalDateTime.class));
            var actual = repository.currentUtcTime();
            var upper = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",
                    (rs, row) -> rs.getObject(1, LocalDateTime.class));
            assertThat(actual).isBetween(Objects.requireNonNull(lower), Objects.requireNonNull(upper));
        });
    }

    private AiUsageResponse query() {
        return service.getUsage(Long.toString(userId));
    }

    private long createUser() {
        return Objects.requireNonNull(new TransactionTemplate(transactions).execute(status ->
                users.saveAndFlush(User.createLocalUser("user@example.com", "hash", "사용자")).getId()));
    }

    private void addSubject(String key) {
        jdbc.update("INSERT IGNORE INTO ai_usage_subjects (subject_key, created_at) VALUES (?, ?)",
                key, NOW);
    }

    private void addDaily(String key, LocalDate day, AiUsageFeature feature, int count) {
        addSubject(key);
        jdbc.update("""
                INSERT INTO ai_usage_subject_daily (subject_key, usage_date, feature, accepted)
                VALUES (?, ?, ?, ?)
                """, key, java.sql.Date.valueOf(day), feature.name(), count);
    }

    private void addReservation(String key, String feature, String state, LocalDateTime createdAt) {
        addSubject(key);
        jdbc.update("""
                        INSERT INTO ai_usage_reservations
                            (id, subject_key, user_id, feature, resource_id, status,
                             lease_expires_at, created_at, finished_at)
                        VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID().toString(), key, feature, ++resourceId, state,
                createdAt.plusSeconds(90), createdAt, "ACTIVE".equals(state) ? null : NOW);
    }

    private Map<String, List<Map<String, Object>>> databaseState() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : TABLES) {
            // TABLES는 고정 식별자 목록이다. 조회 전후 전체 행을 비교한다.
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        }
        return result;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        AiUsageProperties aiUsageProperties() {
            return new AiUsageProperties(true, true, true, AiUsageConfigurationTest.TEST_KEY,
                    3, 15, 30, 3, 20, 2, 50, 500, 10, 90);
        }
    }
}
