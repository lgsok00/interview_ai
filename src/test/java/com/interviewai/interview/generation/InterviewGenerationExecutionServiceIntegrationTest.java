package com.interviewai.interview.generation;

import com.interviewai.global.error.CatalogException;
import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.repository.InterviewSessionRepository;
import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.interviewai.interview.generation.InterviewGenerationPolicy.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({InterviewGenerationExecutionService.class,
        InterviewGenerationExecutionServiceIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InterviewGenerationExecutionServiceIntegrationTest extends MySqlIntegrationTest {
    private final List<Long> userIds = new ArrayList<>();
    @Autowired
    InterviewGenerationExecutionService service;
    @Autowired
    InterviewSessionRepository sessions;
    @Autowired
    UserRepository users;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanup() {
        for (Long id : userIds) jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    @Test
    void appliesV12AndRejectsInvalidLeaseAndDuplicateRegistration() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '12' AND success = TRUE",
                Integer.class)).isEqualTo(1);
        long id = create();
        assertThatThrownBy(() -> jdbc.update("UPDATE interview_generation_jobs SET status = 'RUNNING' WHERE session_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> service.register(id)))
                .isInstanceOf(DataAccessException.class);
        assertThat(job(id, "status")).isEqualTo("PENDING");
    }

    @Test
    void registrationRequiresCallerTransaction() {
        assertThatThrownBy(() -> service.register(1L)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void rollsBackSessionAndJobTogether() {
        long userId = user();
        long[] id = new long[1];
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            id[0] = saveSession(userId);
            service.register(id[0]);
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_sessions WHERE id = ?", Integer.class, id[0])).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_generation_jobs WHERE session_id = ?", Integer.class, id[0])).isZero();
    }

    @Test
    void claimsFrozenConfigurationAndCommitsExactlyOneQuestionSet() {
        long id = create();
        var claim = service.claimNext().orElseThrow();
        assertThat(claim.sessionId()).isEqualTo(id);
        assertThat(claim.model()).isEqualTo("persisted-model");
        assertThat(claim.version()).isEqualTo(VERSION);
        assertThat(claim.attemptCount()).isEqualTo(1);
        assertThat(service.claimNext()).isEmpty();
        assertThat(service.renew(claim)).isTrue();
        assertThat(service.complete(claim, fallback("RAG_EMPTY"))).isTrue();
        assertThat(sessionStatus(id)).isEqualTo("READY");
        assertThat(job(id, "status")).isEqualTo("SUCCEEDED");
        assertThat(job(id, "fallback_reason")).isEqualTo("RAG_EMPTY");
        assertThat(jdbc.queryForList("SELECT sequence_number FROM interview_questions WHERE session_id = ? ORDER BY sequence_number",
                Integer.class, id)).containsExactly(1, 2, 3, 4, 5);
        assertThat(service.complete(claim, fallback("RAG_EMPTY"))).isFalse();
        assertThat(service.renew(claim)).isFalse();
        assertThat(service.fail(claim, "STALE", false)).isFalse();
        assertThat(questionCount(id)).isEqualTo(5);
    }

    @Test
    void rejectsExpiredAndSupersededAttempts() {
        long id = create();
        var first = service.claimNext().orElseThrow();
        expire(id);
        assertThat(service.renew(first)).isFalse();
        assertThat(service.complete(first, fallback("TEST"))).isFalse();
        assertThat(service.fail(first, "STALE", false)).isFalse();
        var second = service.claimNext().orElseThrow();
        assertThat(second.attemptId()).isNotEqualTo(first.attemptId());
        assertThat(second.attemptCount()).isEqualTo(2);
        assertThat(service.complete(first, fallback("TEST"))).isFalse();
        assertThat(service.complete(second, fallback("TEST"))).isTrue();
        assertThat(questionCount(id)).isEqualTo(5);
    }

    @Test
    void schedulesRetriesAndRecoversLastExpiredAttempt() {
        long id = create();
        var first = service.claimNext().orElseThrow();
        assertThat(service.fail(first, "AI_TIMEOUT", true)).isTrue();
        assertThat(sessionStatus(id)).isEqualTo("GENERATING");
        assertThat(job(id, "last_error_code")).isEqualTo("AI_TIMEOUT");
        assertThat(service.claimNext()).isEmpty();
        jdbc.update("UPDATE interview_generation_jobs SET available_at = UTC_TIMESTAMP(6) WHERE session_id = ?", id);
        var second = service.claimNext().orElseThrow();
        expire(id);
        var third = service.claimNext().orElseThrow();
        assertThat(second.attemptCount()).isEqualTo(2);
        assertThat(third.attemptCount()).isEqualTo(3);
        expire(id);
        var recovery = service.claimNext().orElseThrow();
        assertThat(recovery.recoveryOnly()).isTrue();
        assertThat(recovery.attemptCount()).isEqualTo(3);
        assertThat(service.complete(third, fallback("STALE"))).isFalse();
        assertThat(service.complete(recovery, fallback("GENERATION_LEASE_EXHAUSTED"))).isTrue();
    }

    @Test
    void manualRetryChecksOwnershipAndEnforcesTwoRoundLimit() {
        long id = create();
        long userId = owner(id);
        for (int round = 0; round < 3; round++) {
            var claim = service.claimNext().orElseThrow();
            assertThat(service.fail(claim, "AI_REQUEST_REJECTED", false)).isTrue();
            assertThat(sessionStatus(id)).isEqualTo("FAILED");
            assertThatThrownBy(() -> service.retry(userId + 999999, id))
                    .isInstanceOfSatisfying(CatalogException.class,
                            error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
            if (round < 2) {
                service.retry(userId, id);
                assertThat(sessionStatus(id)).isEqualTo("GENERATING");
                assertThat(jdbc.queryForObject("SELECT failure_code FROM interview_sessions WHERE id = ?", String.class, id)).isNull();
            }
        }
        assertThatThrownBy(() -> service.retry(userId, id))
                .isInstanceOfSatisfying(CatalogException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(sessionStatus(id)).isEqualTo("FAILED");
    }

    @Test
    void rollsBackPartialQuestionInsertsAndSuccessState() {
        long id = create();
        var claim = service.claimNext().orElseThrow();
        // Force the second FALLBACK insert to fail after the first insert succeeds.
        jdbc.execute("CREATE UNIQUE INDEX test_generation_insert_failure ON interview_questions (session_id, generation_source)");
        try {
            assertThatThrownBy(() -> service.complete(claim, fallback("TEST")))
                    .isInstanceOf(DataAccessException.class);
            assertThat(questionCount(id)).isZero();
            assertThat(sessionStatus(id)).isEqualTo("GENERATING");
            assertThat(job(id, "status")).isEqualTo("RUNNING");
        } finally {
            // 테스트 실행 중 생성한 임시 인덱스
            // noinspection SqlResolve
            jdbc.execute("DROP INDEX test_generation_insert_failure ON interview_questions");
        }
        assertThat(service.complete(claim, fallback("TEST"))).isTrue();
    }

    @Test
    void deletionCascadesAndRejectsLateCompletion() {
        long id = create();
        var claim = service.claimNext().orElseThrow();
        jdbc.update("DELETE FROM users WHERE id = ?", owner(id));
        assertThat(service.complete(claim, fallback("TEST"))).isFalse();
        assertThat(service.fail(claim, "STALE", false)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_generation_jobs WHERE session_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void preservesUnexpectedExistingQuestionsAndRollsBackJobSuccess() {
        long id = create();
        var claim = service.claimNext().orElseThrow();
        jdbc.update("""
                INSERT INTO interview_questions
                (session_id, sequence_number, question_type, generation_source, content, created_at)
                VALUES (?, 1, 'TECHNICAL', 'FALLBACK', '기존 질문', UTC_TIMESTAMP(6))
                """, id);
        assertThatThrownBy(() -> service.complete(claim, fallback("TEST")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(questionCount(id)).isEqualTo(1);
        assertThat(job(id, "status")).isEqualTo("RUNNING");
        assertThat(sessionStatus(id)).isEqualTo("GENERATING");
    }

    @Test
    void concurrentWorkersClaimOnlyOnce() throws Exception {
        create();
        List<Optional<InterviewGenerationExecutionService.Claim>> results = race(service::claimNext);
        assertThat(results.stream().filter(Optional::isPresent).count()).isEqualTo(1);
    }

    @Test
    void concurrentCompletionsInsertOnlyOneBatch() throws Exception {
        long id = create();
        var claim = service.claimNext().orElseThrow();
        assertThat(race(() -> service.complete(claim, fallback("TEST"))))
                .containsExactlyInAnyOrder(true, false);
        assertThat(questionCount(id)).isEqualTo(5);
    }

    @Test
    void concurrentManualRetriesAcceptOnlyOneRequest() throws Exception {
        long id = create();
        long userId = owner(id);
        service.fail(service.claimNext().orElseThrow(), "AI_REQUEST_REJECTED", false);
        assertThat(race(() -> {
            try {
                service.retry(userId, id);
                return true;
            } catch (CatalogException exception) {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                return false;
            }
        })).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT retry_count FROM interview_generation_jobs WHERE session_id = ?", Integer.class, id)).isEqualTo(1);
    }

    private <T> List<T> race(Callable<T> action) throws Exception {
        // finally에서 강제 종료와 제한 시간 내 종료 여부를 확인한다.
        // noinspection resource
        var executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<T> gated = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return action.call();
        };
        try {
            var first = executor.submit(gated);
            var second = executor.submit(gated);
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private long user() {
        Long id = tx().execute(status -> users.saveAndFlush(User.createLocalUser(
                "generation-" + UUID.randomUUID() + "@example.com", "encoded", "테스트")).getId());
        userIds.add(id);
        return id;
    }

    private long create() {
        long userId = user();
        return tx().execute(status -> {
            long id = saveSession(userId);
            service.register(id);
            return id;
        });
    }

    private long saveSession(long userId) {
        return sessions.saveAndFlush(InterviewSession.create(users.findById(userId).orElseThrow(),
                999L, null, null, 998L, "회사", "공고", "Backend", "생성 당시 공고 본문",
                null, null, null, null, LocalDateTime.now())).getId();
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private long owner(long id) {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT user_id FROM interview_sessions WHERE id = ?",
                        Long.class,
                        id
                ),
                "면접 세션의 user_id는 null일 수 없습니다."
        );
    }

    private void expire(long id) {
        jdbc.update("UPDATE interview_generation_jobs SET lease_expires_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) WHERE session_id = ?", id);
    }

    private int questionCount(long id) {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM interview_questions WHERE session_id = ?",
                        Integer.class,
                        id
                ),
                "질문 개수 조회 결과는 null일 수 없습니다."
        );
    }

    private String sessionStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM interview_sessions WHERE id = ?", String.class, id);
    }

    private String job(long id, String column) {
        if (!List.of("status", "last_error_code", "fallback_reason").contains(column))
            throw new IllegalArgumentException();
        return jdbc.queryForObject("SELECT " + column + " FROM interview_generation_jobs WHERE session_id = ?", String.class, id);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        InterviewGenerationProperties generationProperties() {
            return new InterviewGenerationProperties(false, Mode.AI, "persisted-model");
        }
    }
}
