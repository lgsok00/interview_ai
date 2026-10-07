package com.interviewai.ai.usage;

import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;

@DataJpaTest
@Import({AiUsageRepository.class, AiUsageAdmissionService.class, AiUsageIdentity.class,
        AiUsageAdmissionIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AiUsageAdmissionIntegrationTest extends MySqlIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);
    private static final LocalDateTime NOW = DAY.atTime(10, 0);

    @Autowired
    private AiUsageAdmissionService admissions;
    @Autowired
    private AiUsageIdentity identity;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactions;
    @MockitoSpyBean
    private AiUsageRepository repository;

    private TransactionTemplate tx;
    private AiUsageRepository clockRepository;
    private long userId;
    private String subject;

    private void createDomainWorkFixture() {
        // 호출 트랜잭션의 도메인 작업을 대신한다. 실제 생성 서비스와 worker 연결은 별도로 검증한다.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_usage_test_work (
                    id BIGINT NOT NULL PRIMARY KEY,
                    marker VARCHAR(40) NOT NULL
                )
                """);
    }

    @AfterEach
    void removeDomainWorkFixture() {
        //noinspection SqlResolve
        jdbc.execute("DROP TABLE IF EXISTS ai_usage_test_work");
    }

    @BeforeEach
    void setUp() {
        createDomainWorkFixture();
        // Testcontainers 전용 DB의 데이터를 매 테스트 전에 초기화한다.
        //noinspection SqlResolve,SqlWithoutWhere
        jdbc.update("DELETE FROM ai_usage_test_work");
        //noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM ai_usage_reservations");
        //noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM ai_usage_subject_daily");
        //noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM ai_usage_global_daily");
        //noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM ai_usage_admin_embedding_minutes");
        //noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM ai_usage_subjects");
        //noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM users");
        // Stub the spy target rather than entering the MANDATORY transaction proxy during setup.
        clockRepository = AopTestUtils.getUltimateTargetObject(repository);
        doReturn(NOW).when(clockRepository).currentUtcTime();
        tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setTimeout(20);
        userId = createUser("user@example.com");
        subject = identity.subjectKey("user@example.com");
    }

    @ParameterizedTest
    @EnumSource(value = AiUsageFeature.class, names = "PERSONAL_EMBEDDING", mode = EnumSource.Mode.EXCLUDE)
    void commitsDomainWorkCountersAndReservationTogether(AiUsageFeature feature) {
        var result = register(userId, feature, 42, 42);
        assertThat(result.value()).isEqualTo("work-42");
        assertThat(count("ai_usage_test_work")).isEqualTo(1);
        assertThat(globalUsed(DAY)).isEqualTo(1);
        assertThat(subjectUsed(subject, DAY, feature)).isEqualTo(1);
        assertThat(activeCount()).isEqualTo(1);
        var row = jdbc.queryForMap("SELECT * FROM ai_usage_reservations WHERE id = ?",
                result.reservationId().toString());
        assertThat(row.get("subject_key")).isEqualTo(subject);
        assertThat(((Number) Objects.requireNonNull(row.get("user_id"), "예약 사용자 ID가 필요합니다."))
                .longValue()).isEqualTo(userId);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("feature")).isEqualTo(feature.name());
        assertThat(((Number) Objects.requireNonNull(row.get("resource_id"), "예약 대상 ID가 필요합니다."))
                .longValue()).isEqualTo(42);
        if (feature == AiUsageFeature.FOLLOW_UP) {
            assertThat(row.get("worker_attempt_id")).isEqualTo(result.reservationId().toString());
            LocalDateTime lease = jdbc.queryForObject("""
                    SELECT lease_expires_at FROM ai_usage_reservations WHERE id = ?
                    """, (rs, n) -> rs.getObject(1, LocalDateTime.class), result.reservationId().toString());
            assertThat(lease).isEqualTo(NOW.plusSeconds(90));
        } else {
            assertThat(row.get("worker_attempt_id")).isNull();
            assertThat(row.get("lease_expires_at")).isNull();
        }
    }

    @Test
    void preservesFeatureCountersButSharesUserSlots() {
        register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1);
        register(userId, AiUsageFeature.ANSWER_EVALUATION, 1, 2);
        assertRejected("AI_PENDING_LIMIT_EXCEEDED",
                () -> register(userId, AiUsageFeature.COVER_LETTER_DRAFT, 1, 3));
        assertThat(subjectUsed(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(1);
        assertThat(subjectUsed(subject, DAY, AiUsageFeature.ANSWER_EVALUATION)).isEqualTo(1);
        assertThat(globalUsed(DAY)).isEqualTo(2);
        assertThat(count("ai_usage_test_work")).isEqualTo(2);
        assertThat(activeCount()).isEqualTo(2);
        assertThat(count("ai_usage_subject_daily")).isEqualTo(2);
    }

    @Test
    void lastDailyAdmissionSucceedsAndNextAdmissionRollsBackDomainWork() {
        seedDaily(subject, 2, 2);
        register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1);
        assertRejected("AI_DAILY_LIMIT_EXCEEDED",
                () -> register(userId, AiUsageFeature.INITIAL_QUESTIONS, 2, 2));
        assertThat(subjectUsed(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(3);
        assertThat(globalUsed(DAY)).isEqualTo(3);
        assertThat(count("ai_usage_test_work")).isEqualTo(1);
        assertThat(activeCount()).isEqualTo(1);
    }

    @Test
    void exhaustedGlobalDayRollsBackNewSubjectAndDomainWork() {
        jdbc.update("INSERT INTO ai_usage_global_daily VALUES (?, 500)", DAY);
        assertRejected("AI_CAPACITY_EXCEEDED",
                () -> register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1));
        assertThat(count("ai_usage_subjects")).isZero();
        assertThat(count("ai_usage_subject_daily")).isZero();
        assertThat(count("ai_usage_test_work")).isZero();
        assertThat(activeCount()).isZero();
        assertThat(globalUsed(DAY)).isEqualTo(500);
    }

    @Test
    void existingResultDoesNotConsumeExhaustedQuotaOrCreateReservation() {
        seedDaily(subject, 3, 500);
        var result = tx.execute(status -> admissions.admitChat(userId, AiUsageFeature.INITIAL_QUESTIONS,
                () -> AiUsageAdmissionService.Registration.existing("saved-result")));
        assertThat(result).isNotNull();
        assertThat(result.value()).isEqualTo("saved-result");
        assertThat(result.reservationId()).isNull();
        assertThat(globalUsed(DAY)).isEqualTo(500);
        assertThat(subjectUsed(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(3);
        assertThat(activeCount()).isZero();
    }

    @Test
    void callbackFailureRollsBackDomainWorkAndSubjectCreation() {
        assertThatThrownBy(() -> tx.execute(status -> admissions.admitChat(userId,
                AiUsageFeature.INITIAL_QUESTIONS, () -> {
                    work(1);
                    throw new IllegalStateException("invalid domain state");
                }))).isInstanceOf(IllegalStateException.class).hasMessage("invalid domain state");
        assertEmptyUsageAndWork();
    }

    @Test
    void failureAfterSuccessfulAdmissionRollsBackAllWrites() {
        assertThatThrownBy(() -> tx.execute(status -> {
            admissions.admitChat(userId, AiUsageFeature.INITIAL_QUESTIONS, () -> {
                work(1);
                return AiUsageAdmissionService.Registration.created(1, "job");
            });
            throw new IllegalStateException("caller failed");
        })).isInstanceOf(IllegalStateException.class).hasMessage("caller failed");
        assertEmptyUsageAndWork();
    }

    @Test
    void quotaRejectionRollsBackJpaDomainMutationAndJdbcWritesTogether() {
        seedDaily(subject, 3, 3);
        assertRejected("AI_DAILY_LIMIT_EXCEEDED", () -> tx.execute(status ->
                admissions.admitChat(userId, AiUsageFeature.INITIAL_QUESTIONS, () -> {
                    User user = users.findById(userId).orElseThrow();
                    user.updateNickname("변경된 닉네임");
                    users.flush();
                    work(1);
                    return AiUsageAdmissionService.Registration.created(1, "job");
                })));
        assertThat(jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("테스트 사용자");
        assertThat(count("ai_usage_test_work")).isZero();
        assertThat(subjectUsed(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(3);
        assertThat(globalUsed(DAY)).isEqualTo(3);
        assertThat(activeCount()).isZero();
    }

    @Test
    void catchingAdmissionFailureCannotCommitRejectedDomainWork() {
        seedDaily(subject, 3, 3);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            try {
                admissions.admitChat(userId, AiUsageFeature.INITIAL_QUESTIONS, () -> {
                    work(1);
                    return AiUsageAdmissionService.Registration.created(1, "job");
                });
            } catch (AiUsageException exception) {
                assertThat(exception.getCode()).isEqualTo("AI_DAILY_LIMIT_EXCEEDED");
            }
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(count("ai_usage_test_work")).isZero();
        assertThat(globalUsed(DAY)).isEqualTo(3);
        assertThat(subjectUsed(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(3);
        assertThat(activeCount()).isZero();
    }

    @Test
    void reservationInsertFailureRollsBackPreviouslyIncrementedCounters() {
        seedReservation(subject, 42, AiUsageFeature.INITIAL_QUESTIONS, "FINISHED");
        // 임시 UNIQUE 색인으로 마지막 INSERT를 실패시킨다. 트리거 생성 권한은 필요하지 않다.
        jdbc.execute("CREATE UNIQUE INDEX test_ai_usage_resource_failure ON ai_usage_reservations(resource_id)");
        try {
            assertRejected("AI_USAGE_UNAVAILABLE",
                    () -> register(userId, AiUsageFeature.INITIAL_QUESTIONS, 42, 1));
            assertThat(count("ai_usage_global_daily")).isZero();
            assertThat(count("ai_usage_subject_daily")).isZero();
            assertThat(count("ai_usage_test_work")).isZero();
            assertThat(count("ai_usage_reservations")).isEqualTo(1);
            assertThat(activeCount()).isZero();
        } finally {
            // 테스트 실행 중 생성한 색인은 IDE의 DB 스키마에 없다.
            //noinspection SqlResolve
            jdbc.execute("DROP INDEX test_ai_usage_resource_failure ON ai_usage_reservations");
        }
    }

    @Test
    void duplicateActiveResourceDoesNotChargeOrKeepNewDomainWork() {
        register(userId, AiUsageFeature.FOLLOW_UP, 42, 1);
        assertRejected("AI_REQUEST_IN_PROGRESS", () -> register(userId, AiUsageFeature.FOLLOW_UP, 42, 2));
        assertThat(globalUsed(DAY)).isEqualTo(1);
        assertThat(subjectUsed(subject, DAY, AiUsageFeature.FOLLOW_UP)).isEqualTo(1);
        assertThat(count("ai_usage_test_work")).isEqualTo(1);
        assertThat(activeCount()).isEqualTo(1);
    }

    @Test
    void manuallyFinishedReservationAllowsNewAdmissionWithoutRefundingDailyUsage() {
        var first = register(userId, AiUsageFeature.INITIAL_QUESTIONS, 42, 1);
        jdbc.update("UPDATE ai_usage_reservations SET status = 'FINISHED', finished_at = ? WHERE id = ?",
                NOW.plusSeconds(1), first.reservationId().toString());
        var next = register(userId, AiUsageFeature.INITIAL_QUESTIONS, 42, 2);
        assertThat(next.reservationId()).isNotEqualTo(first.reservationId());
        assertThat(globalUsed(DAY)).isEqualTo(2);
        assertThat(subjectUsed(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(2);
        assertThat(activeCount()).isEqualTo(1);
    }

    @Test
    void midnightStartsNewCountersButDoesNotReleaseExistingSlots() {
        register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1);
        register(userId, AiUsageFeature.ANSWER_EVALUATION, 2, 2);
        doReturn(DAY.atTime(15, 0)).when(clockRepository).currentUtcTime();
        assertRejected("AI_PENDING_LIMIT_EXCEEDED",
                () -> register(userId, AiUsageFeature.INITIAL_QUESTIONS, 3, 3));
        assertThat(activeCount()).isEqualTo(2);
        assertThat(globalUsed(DAY)).isEqualTo(2);
        assertThat(count("ai_usage_global_daily")).isEqualTo(1);
        jdbc.update("UPDATE ai_usage_reservations SET status = 'FINISHED', finished_at = ? WHERE resource_id = 1",
                DAY.atTime(15, 0));
        register(userId, AiUsageFeature.INITIAL_QUESTIONS, 3, 3);
        assertThat(globalUsed(DAY.plusDays(1))).isEqualTo(1);
        assertThat(subjectUsed(subject, DAY.plusDays(1), AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(1);
        assertThat(subjectUsed(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(1);
        assertThat(activeCount()).isEqualTo(2);
    }

    @Test
    void crossingMidnightDuringDomainWorkChargesNewKstDay() {
        doReturn(DAY.atTime(14, 59, 59, 999999000), DAY.atTime(15, 0))
                .when(clockRepository).currentUtcTime();
        register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1);
        assertThat(globalUsed(DAY.plusDays(1))).isEqualTo(1);
        assertThat(subjectUsed(subject, DAY.plusDays(1), AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(1);
        assertThat(count("ai_usage_global_daily")).isEqualTo(1);
    }

    @Test
    void deletedAndRecreatedAccountRetainsSameEmailDailyQuota() {
        seedDaily(subject, 3, 3);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        long replacement = createUser("user@example.com");
        assertThat(replacement).isNotEqualTo(userId);
        assertRejected("AI_DAILY_LIMIT_EXCEEDED",
                () -> register(replacement, AiUsageFeature.INITIAL_QUESTIONS, 1, 1));
        assertThat(subjectUsed(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(3);
        assertThat(count("ai_usage_subjects")).isEqualTo(1);
        assertThat(count("ai_usage_test_work")).isZero();
    }

    @Test
    void deletedAccountsActiveReservationStillCountsUntilExplicitCleanup() {
        register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1);
        register(userId, AiUsageFeature.ANSWER_EVALUATION, 2, 2);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_usage_reservations WHERE user_id IS NULL",
                Integer.class)).isEqualTo(2);
        long replacement = createUser("user@example.com");
        assertRejected("AI_PENDING_LIMIT_EXCEEDED",
                () -> register(replacement, AiUsageFeature.COVER_LETTER_DRAFT, 3, 3));
        assertThat(activeCount()).isEqualTo(2);
        assertThat(globalUsed(DAY)).isEqualTo(2);
    }

    @Test
    void suspendedAccountDoesNotRegisterDomainWork() {
        jdbc.update("UPDATE users SET status = 'SUSPENDED', suspended_at = ? WHERE id = ?",
                NOW, userId);
        assertThatThrownBy(() -> register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1))
                .isInstanceOf(com.interviewai.global.error.CatalogException.class)
                .satisfies(error -> assertThat(((com.interviewai.global.error.CatalogException) error).getCode())
                        .isEqualTo("USER_SUSPENDED"));
        assertEmptyUsageAndWork();
    }

    @Test
    void springProxyRequiresExistingTransaction() {
        assertThatThrownBy(() -> admissions.admitChat(userId, AiUsageFeature.INITIAL_QUESTIONS,
                () -> AiUsageAdmissionService.Registration.created(1, "job")))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertEmptyUsageAndWork();
    }

    @Test
    void readOnlyTransactionIsRejectedBeforeDomainWork() {
        var readOnly = new TransactionTemplate(transactions);
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.execute(status -> admissions.admitChat(userId,
                AiUsageFeature.INITIAL_QUESTIONS, () -> {
                    work(1);
                    return AiUsageAdmissionService.Registration.created(1, "job");
                }))).isInstanceOf(IllegalStateException.class);
        assertEmptyUsageAndWork();
    }

    @Test
    void missingGlobalLockRowFailsClosedBeforeDomainWork() {
        // 전역 잠금 행 누락 상황을 재현하고 finally에서 복원한다.
        //noinspection SqlWithoutWhere
        jdbc.update("DELETE FROM ai_usage_global_state");
        try {
            assertRejected("AI_USAGE_UNAVAILABLE",
                    () -> register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1));
            assertEmptyUsageAndWork();
        } finally {
            jdbc.update("INSERT INTO ai_usage_global_state(id) VALUES (1)");
        }
    }

    @Test
    void productionRepositoryReadsDatabaseUtcClock() {
        doCallRealMethod().when(clockRepository).currentUtcTime();
        tx.executeWithoutResult(status -> {
            LocalDateTime before = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",
                    (rs, n) -> rs.getObject(1, LocalDateTime.class));
            LocalDateTime actual = repository.currentUtcTime();
            LocalDateTime after = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",
                    (rs, n) -> rs.getObject(1, LocalDateTime.class));
            assertThat(actual).isBetween(before, after);
        });
    }

    @Test
    void concurrentFirstAdmissionsCreateOneGlobalDayAndSeparateSubjects() throws Exception {
        long otherUser = createUser("other@example.com");
        assertThat(race(
                () -> register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1),
                () -> register(otherUser, AiUsageFeature.INITIAL_QUESTIONS, 2, 2)))
                .containsExactly("OK", "OK");
        assertThat(count("ai_usage_subjects")).isEqualTo(2);
        assertThat(count("ai_usage_global_daily")).isEqualTo(1);
        assertThat(globalUsed(DAY)).isEqualTo(2);
        assertThat(activeCount()).isEqualTo(2);
        assertThat(count("ai_usage_test_work")).isEqualTo(2);
    }

    @Test
    void concurrentRequestsCannotBothConsumeLastUserDailyAdmission() throws Exception {
        seedDaily(subject, 2, 2);
        assertThat(race(
                () -> register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1),
                () -> register(userId, AiUsageFeature.INITIAL_QUESTIONS, 2, 2)))
                .containsExactlyInAnyOrder("OK", "AI_DAILY_LIMIT_EXCEEDED");
        assertThat(subjectUsed(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(3);
        assertThat(globalUsed(DAY)).isEqualTo(3);
        assertThat(count("ai_usage_test_work")).isEqualTo(1);
        assertThat(activeCount()).isEqualTo(1);
    }

    @Test
    void concurrentRequestsCannotBothConsumeLastUserSlot() throws Exception {
        seedReservation(subject, 100, AiUsageFeature.ANSWER_EVALUATION, "ACTIVE");
        assertThat(race(
                () -> register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1),
                () -> register(userId, AiUsageFeature.COVER_LETTER_DRAFT, 2, 2)))
                .containsExactlyInAnyOrder("OK", "AI_PENDING_LIMIT_EXCEEDED");
        assertThat(activeCount()).isEqualTo(2);
        assertThat(globalUsed(DAY)).isEqualTo(1);
        assertThat(count("ai_usage_test_work")).isEqualTo(1);
    }

    @Test
    void concurrentDifferentUsersCannotBothConsumeLastGlobalDailyAdmission() throws Exception {
        long otherUser = createUser("other@example.com");
        jdbc.update("INSERT INTO ai_usage_global_daily VALUES (?, 499)", DAY);
        assertThat(race(
                () -> register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1),
                () -> register(otherUser, AiUsageFeature.INITIAL_QUESTIONS, 2, 2)))
                .containsExactlyInAnyOrder("OK", "AI_CAPACITY_EXCEEDED");
        assertThat(globalUsed(DAY)).isEqualTo(500);
        assertThat(activeCount()).isEqualTo(1);
        assertThat(count("ai_usage_test_work")).isEqualTo(1);
        assertThat(count("ai_usage_subject_daily")).isEqualTo(1);
    }

    @Test
    void concurrentDifferentUsersCannotBothConsumeLastGlobalSlot() throws Exception {
        long otherUser = createUser("other@example.com");
        String backgroundSubject = identity.subjectKey("background@example.com");
        for (long id = 100; id < 149; id++) {
            seedReservation(backgroundSubject, id, AiUsageFeature.INITIAL_QUESTIONS, "ACTIVE");
        }
        assertThat(race(
                () -> register(userId, AiUsageFeature.INITIAL_QUESTIONS, 1, 1),
                () -> register(otherUser, AiUsageFeature.INITIAL_QUESTIONS, 2, 2)))
                .containsExactlyInAnyOrder("OK", "AI_CAPACITY_EXCEEDED");
        assertThat(activeCount()).isEqualTo(50);
        assertThat(globalUsed(DAY)).isEqualTo(1);
        assertThat(count("ai_usage_test_work")).isEqualTo(1);
    }

    @Test
    void concurrentFollowUpRequestsReserveSameParentOnlyOnce() throws Exception {
        assertThat(race(
                () -> register(userId, AiUsageFeature.FOLLOW_UP, 42, 1),
                () -> register(userId, AiUsageFeature.FOLLOW_UP, 42, 2)))
                .containsExactlyInAnyOrder("OK", "AI_REQUEST_IN_PROGRESS");
        assertThat(activeCount()).isEqualTo(1);
        assertThat(globalUsed(DAY)).isEqualTo(1);
        assertThat(subjectUsed(subject, DAY, AiUsageFeature.FOLLOW_UP)).isEqualTo(1);
        assertThat(count("ai_usage_test_work")).isEqualTo(1);
    }

    @Test
    void lockingReadsObserveCommittedQuotaDespiteEarlierRepeatableReadSnapshot() {
        long otherUser = createUser("other@example.com");
        jdbc.update("INSERT INTO ai_usage_global_daily VALUES (?, 499)", DAY);
        try (var executor = Executors.newSingleThreadExecutor()) {
            try {
                assertRejected("AI_CAPACITY_EXCEEDED", () -> tx.execute(status -> {
                    assertThat(globalUsed(DAY)).isEqualTo(499); // establish an older consistent-read snapshot
                    var future = executor.submit(() -> register(otherUser, AiUsageFeature.INITIAL_QUESTIONS, 2, 2));
                    try {
                        future.get(15, TimeUnit.SECONDS);
                    } catch (Exception exception) {
                        throw new IllegalStateException("parallel admission failed", exception);
                    }
                    return admissions.admitChat(userId, AiUsageFeature.INITIAL_QUESTIONS, () -> {
                        work(1);
                        return AiUsageAdmissionService.Registration.created(1, "job");
                    });
                }));
            } finally {
                executor.shutdownNow();
            }
        }
        assertThat(globalUsed(DAY)).isEqualTo(500);
        assertThat(count("ai_usage_test_work")).isEqualTo(1);
        assertThat(activeCount()).isEqualTo(1);
    }

    private long createUser(String email) {
        return users.saveAndFlush(User.createLocalUser(email, "test-only-encoded", "테스트 사용자")).getId();
    }

    private AiUsageAdmissionService.Admission<String> register(
            long owner, AiUsageFeature feature, long resourceId, long workId
    ) {
        return Objects.requireNonNull(tx.execute(status -> admissions.admitChat(owner, feature, () -> {
            work(workId);
            return AiUsageAdmissionService.Registration.created(resourceId, "work-" + workId);
        })));
    }

    private void work(long id) {
        // 테스트 실행 중 생성한 테이블과 열은 IDE의 DB 스키마에 없다.
        //noinspection SqlResolve
        jdbc.update("INSERT INTO ai_usage_test_work(id, marker) VALUES (?, ?)", id, "work-" + id);
    }

    private void seedSubject(String key) {
        jdbc.update("""
                INSERT INTO ai_usage_subjects(subject_key, created_at) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE subject_key = subject_key
                """, key, NOW);
    }

    private void seedDaily(String key, int used, int global) {
        seedSubject(key);
        jdbc.update("INSERT INTO ai_usage_subject_daily VALUES (?, ?, ?, ?)",
                key, DAY, AiUsageFeature.INITIAL_QUESTIONS.name(), used);
        jdbc.update("INSERT INTO ai_usage_global_daily VALUES (?, ?)", DAY, global);
    }

    private void seedReservation(String key, long resourceId, AiUsageFeature feature, String status) {
        seedSubject(key);
        jdbc.update("""
                        INSERT INTO ai_usage_reservations
                            (id, subject_key, feature, resource_id, status, created_at, finished_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID().toString(), key, feature.name(), resourceId, status,
                NOW, status.equals("ACTIVE") ? null : NOW.plusSeconds(1));
    }

    private int globalUsed(LocalDate date) {
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT chat_accepted FROM ai_usage_global_daily WHERE usage_date = ?",
                        Integer.class, date),
                "전역 일일 사용량 조회 결과가 필요합니다."
        );
    }

    private int subjectUsed(String key, LocalDate date, AiUsageFeature feature) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                SELECT accepted FROM ai_usage_subject_daily WHERE subject_key = ? AND usage_date = ? AND feature = ?
                """, Integer.class, key, date, feature.name()), "주체 일일 사용량 조회 결과가 필요합니다.");
    }

    private int count(String table) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class),
                "테이블 행 수 조회 결과가 필요합니다.");
    }

    private int activeCount() {
        return Objects.requireNonNull(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ai_usage_reservations WHERE status = 'ACTIVE'", Integer.class),
                "활성 예약 수 조회 결과가 필요합니다.");
    }

    private void assertEmptyUsageAndWork() {
        assertThat(count("ai_usage_test_work")).isZero();
        assertThat(count("ai_usage_subjects")).isZero();
        assertThat(count("ai_usage_subject_daily")).isZero();
        assertThat(count("ai_usage_global_daily")).isZero();
        assertThat(count("ai_usage_reservations")).isZero();
    }

    private void assertRejected(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(AiUsageException.class)
                .satisfies(error -> assertThat(((AiUsageException) error).getCode()).isEqualTo(code));
    }

    private List<String> race(Runnable first, Runnable second) throws Exception {
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var a = executor.submit(task(start, first));
                var b = executor.submit(task(start, second));
                return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private Callable<String> task(CyclicBarrier start, Runnable action) {
        return () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                action.run();
                return "OK";
            } catch (AiUsageException exception) {
                return exception.getCode();
            }
        };
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
