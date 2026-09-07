package com.interviewai.rag.service;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.entity.RagIndexJobEntity;
import com.interviewai.rag.index.RagIndexJobStatus;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.index.RagIndexTarget;
import com.interviewai.rag.repository.RagIndexJobRepository;
import com.interviewai.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({RagIndexSequenceService.class, RagIndexJobRegistrationService.class,
        RagIndexJobRegistrationServiceIntegrationTest.TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RagIndexJobRegistrationServiceIntegrationTest extends MySqlIntegrationTest {

    private static final long SOURCE_ID = 970001L;
    private static final RagSourceKey KEY = new RagSourceKey(RagSourceType.COMPANY, SOURCE_ID);
    private static final Instant NOW = Instant.parse("2026-09-07T01:02:03.123456789Z");

    @Autowired RagIndexJobRegistrationService service;
    @Autowired RagIndexJobRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @BeforeEach
    @AfterEach
    void clearTestSources() {
        jdbc.update("DELETE FROM rag_index_jobs WHERE source_id IN (?, ?)", SOURCE_ID, SOURCE_ID + 1);
        jdbc.update("DELETE FROM rag_index_sources WHERE source_id IN (?, ?)", SOURCE_ID, SOURCE_ID + 1);
    }

    @Test
    void appliesV7Migration() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7' AND success = TRUE",
                Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(RagSourceType.class)
    void persistsUpsertSnapshotAcrossTransactions(RagSourceType type) {
        RagIndexTarget target = target(type);
        Long id = transaction().execute(status -> service.registerUpsert(target, 1).getId());
        RagIndexJobEntity loaded = load(id);
        assertThat(loaded.getTarget()).isEqualTo(target);
        assertThat(loaded.getSourceKey()).isEqualTo(target.snapshot().sourceKey());
        assertThat(loaded.getOperation()).isEqualTo(RagIndexOperation.UPSERT);
        assertInitialState(loaded);
        assertThat(sequence(target.snapshot().sourceKey())).isEqualTo(1L);
    }

    @ParameterizedTest
    @EnumSource(RagSourceType.class)
    void registersDeleteWithoutOriginalDocument(RagSourceType type) {
        RagSourceKey key = new RagSourceKey(type, SOURCE_ID);
        RagIndexJobEntity loaded = load(registerDelete(key).getId());
        assertThat(loaded.getSourceKey()).isEqualTo(key);
        assertThat(loaded.getOperation()).isEqualTo(RagIndexOperation.DELETE);
        assertThat(loaded.getTarget()).isNull();
        assertThat(loaded.getOwnerUserId()).isNull();
        assertThat(loaded.getCompanyId()).isNull();
        assertThat(loaded.getSnapshotTitle()).isNull();
        assertThat(loaded.getSnapshotContent()).isNull();
        assertThat(loaded.getSourceRevision()).isNull();
        assertThat(loaded.getPipelineVersion()).isNull();
        assertInitialState(loaded);
    }

    @Test
    void allocatesUpsertAndDeleteInOneTransaction() {
        List<Long> ids = transaction().execute(status -> List.of(
                service.registerUpsert(target(RagSourceType.COMPANY), 1).getId(),
                service.registerDelete(KEY, 1).getId()));
        assertThat(load(ids.getFirst()).getSourceSequence()).isEqualTo(1L);
        assertThat(load(ids.getLast()).getSourceSequence()).isEqualTo(2L);
        assertThat(sequence(KEY)).isEqualTo(2L);
        assertThat(jobCount()).isEqualTo(2);
    }

    @Test
    void separatesDifferentSourceTypesAndIds() {
        assertThat(registerDelete(KEY).getSourceSequence()).isEqualTo(1);
        assertThat(registerDelete(new RagSourceKey(RagSourceType.RESUME, SOURCE_ID))
                .getSourceSequence()).isEqualTo(1);
        assertThat(registerDelete(new RagSourceKey(RagSourceType.COMPANY, SOURCE_ID + 1))
                .getSourceSequence()).isEqualTo(1);
        assertThat(registerDelete(KEY).getSourceSequence()).isEqualTo(2);
        assertThat(jobCount()).isEqualTo(4);
    }

    @Test
    void requiresCallerTransactionForBothOperations() {
        assertThatThrownBy(() -> service.registerDelete(KEY, 1))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> service.registerUpsert(target(RagSourceType.COMPANY), 1))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertNoRows();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidAttemptsWithoutAllocatingSequence(int attempts) {
        assertThatIllegalArgumentException().isThrownBy(() -> transaction().executeWithoutResult(status ->
                service.registerDelete(KEY, attempts)));
        assertThatIllegalArgumentException().isThrownBy(() -> transaction().executeWithoutResult(status ->
                service.registerUpsert(target(RagSourceType.COMPANY), attempts)));
        assertNoRows();
    }

    @Test
    void rejectsNullInputsWithoutAllocatingSequence() {
        assertThatNullPointerException().isThrownBy(() -> transaction().executeWithoutResult(status ->
                service.registerDelete(null, 1)));
        assertThatNullPointerException().isThrownBy(() -> transaction().executeWithoutResult(status ->
                service.registerUpsert(null, 1)));
        assertNoRows();
    }

    @Test
    void rollsBackNewSourceAndInsertedJobOnCallerFailure() {
        assertThatIllegalStateException().isThrownBy(() -> transaction().executeWithoutResult(status -> {
            service.registerUpsert(target(RagSourceType.COMPANY), 1);
            entityManager.flush();
            throw new IllegalStateException("caller failed");
        })).withMessage("caller failed");
        assertNoRows();
        assertThat(registerDelete(KEY).getSourceSequence()).isEqualTo(1L);
    }

    @Test
    void rollsBackExistingSequenceVersionAndMultipleInsertedJobs() {
        registerDelete(KEY);
        Long originalVersion = version();
        transaction().executeWithoutResult(status -> {
            service.registerUpsert(target(RagSourceType.COMPANY), 1);
            service.registerDelete(KEY, 1);
            entityManager.flush();
            status.setRollbackOnly();
        });
        assertThat(jobCount()).isEqualTo(1);
        assertThat(sequence(KEY)).isEqualTo(1L);
        assertThat(version()).isEqualTo(originalVersion);
        assertThat(registerDelete(KEY).getSourceSequence()).isEqualTo(2L);
    }

    @Test
    void rollsBackSequenceWhenActualJobInsertFails() {
        registerDelete(KEY);
        // DB에 다음 순번을 미리 점유하여 실제 등록 INSERT의 unique 충돌을 유도한다.
        transaction().executeWithoutResult(status ->
                repository.save(RagIndexJobEntity.delete(KEY, 2, 1, NOW)));
        Long originalVersion = version();
        Throwable failure = catchThrowable(() -> registerDelete(KEY));
        assertSqlFailure(failure, 1062, "uk_rag_index_jobs_source_sequence");
        assertThat(sequence(KEY)).isEqualTo(1L);
        assertThat(version()).isEqualTo(originalVersion);
        assertThat(jobCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void serializesConcurrentRegistrations(boolean existing) throws Exception {
        if (existing) {
            registerDelete(KEY);
        }
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Long> task = () -> transaction().execute(status -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return service.registerDelete(KEY, 1).getSourceSequence();
            });
            var first = executor.submit(task);
            var second = executor.submit(task);
            try {
                long base = existing ? 1 : 0;
                assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(base + 1, base + 2);
                assertThat(sequence(KEY)).isEqualTo(base + 2);
                assertThat(jobCount()).isEqualTo((int) base + 2);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "source_sequence = 0, chk_rag_index_jobs_sequence",
            "status = 'UNKNOWN', chk_rag_index_jobs_status",
            "max_attempts = 0, chk_rag_index_jobs_attempts",
            "attempt_count = -1, chk_rag_index_jobs_attempts",
            "attempt_count = 2, chk_rag_index_jobs_attempts",
            "lock_version = -1, chk_rag_index_jobs_lock_version",
            "updated_at = '2020-01-01 00:00:00', chk_rag_index_jobs_time",
            "snapshot_content = NULL, chk_rag_index_jobs_payload",
            "snapshot_title = ' ', chk_rag_index_jobs_payload",
            "source_revision = NULL, chk_rag_index_jobs_payload",
            "pipeline_version = NULL, chk_rag_index_jobs_payload",
            "owner_user_id = 1, chk_rag_index_jobs_payload",
            "company_id = NULL, chk_rag_index_jobs_payload",
            "company_id = 1, chk_rag_index_jobs_payload",
            "operation = 'DELETE', chk_rag_index_jobs_payload"
    })
    void databaseRejectsInvalidJobState(String assignment, String constraint) {
        Long id = transaction().execute(status ->
                service.registerUpsert(target(RagSourceType.COMPANY), 1).getId());
        Throwable failure = catchThrowable(() -> updateInvalidJobState(assignment, id));
        assertSqlFailure(failure, 3819, constraint);
        assertThat(load(id).getTarget()).isEqualTo(target(RagSourceType.COMPANY));
    }

    @Test
    void foreignKeyRejectsJobWithoutSourceManagementRow() {
        Throwable failure = catchThrowable(() -> transaction().executeWithoutResult(status ->
                repository.save(RagIndexJobEntity.delete(KEY, 1, 1, NOW))));
        assertSqlFailure(failure, 1452, "fk_rag_index_jobs_source");
        assertNoRows();
    }

    @ParameterizedTest
    @EnumSource(value = RagSourceType.class, names = {"JOB_POSTING", "COVER_LETTER", "RESUME"})
    void databaseRejectsMissingRequiredOwnershipMetadata(RagSourceType type) {
        Long id = transaction().execute(status -> service.registerUpsert(target(type), 1).getId());
        Throwable failure = catchThrowable(() -> {
            if (type.isPrivate()) {
                jdbc.update("UPDATE rag_index_jobs SET owner_user_id = NULL WHERE id = ?", id);
            } else {
                jdbc.update("UPDATE rag_index_jobs SET company_id = NULL WHERE id = ?", id);
            }
        });
        assertSqlFailure(failure, 3819, "chk_rag_index_jobs_payload");
        assertThat(load(id).getTarget()).isEqualTo(target(type));
    }

    @Test
    void overflowDoesNotInsertJobOrChangeSourceVersion() {
        registerDelete(KEY);
        jdbc.update("""
                UPDATE rag_index_sources SET last_sequence = ? WHERE source_type = ? AND source_id = ?
                """, Long.MAX_VALUE, KEY.sourceType().name(), KEY.sourceId());
        Long originalVersion = version();
        assertThatThrownBy(() -> registerDelete(KEY)).isInstanceOf(ArithmeticException.class);
        assertThat(sequence(KEY)).isEqualTo(Long.MAX_VALUE);
        assertThat(version()).isEqualTo(originalVersion);
        assertThat(jobCount()).isEqualTo(1);
    }

    private void updateInvalidJobState(String assignment, Long id) {
        switch (assignment) {
            case "source_sequence = 0" -> jdbc.update(
                    "UPDATE rag_index_jobs SET source_sequence = ? WHERE id = ?", 0, id);
            case "status = 'UNKNOWN'" -> jdbc.update(
                    "UPDATE rag_index_jobs SET status = ? WHERE id = ?", "UNKNOWN", id);
            case "max_attempts = 0" -> jdbc.update(
                    "UPDATE rag_index_jobs SET max_attempts = ? WHERE id = ?", 0, id);
            case "attempt_count = -1" -> jdbc.update(
                    "UPDATE rag_index_jobs SET attempt_count = ? WHERE id = ?", -1, id);
            case "attempt_count = 2" -> jdbc.update(
                    "UPDATE rag_index_jobs SET attempt_count = ? WHERE id = ?", 2, id);
            case "lock_version = -1" -> jdbc.update(
                    "UPDATE rag_index_jobs SET lock_version = ? WHERE id = ?", -1, id);
            case "updated_at = '2020-01-01 00:00:00'" -> jdbc.update(
                    "UPDATE rag_index_jobs SET updated_at = ? WHERE id = ?", "2020-01-01 00:00:00", id);
            case "snapshot_content = NULL" -> jdbc.update(
                    "UPDATE rag_index_jobs SET snapshot_content = ? WHERE id = ?", null, id);
            case "snapshot_title = ' '" -> jdbc.update(
                    "UPDATE rag_index_jobs SET snapshot_title = ? WHERE id = ?", " ", id);
            case "source_revision = NULL" -> jdbc.update(
                    "UPDATE rag_index_jobs SET source_revision = ? WHERE id = ?", null, id);
            case "pipeline_version = NULL" -> jdbc.update(
                    "UPDATE rag_index_jobs SET pipeline_version = ? WHERE id = ?", null, id);
            case "owner_user_id = 1" -> jdbc.update(
                    "UPDATE rag_index_jobs SET owner_user_id = ? WHERE id = ?", 1, id);
            case "company_id = NULL" -> jdbc.update(
                    "UPDATE rag_index_jobs SET company_id = ? WHERE id = ?", null, id);
            case "company_id = 1" -> jdbc.update(
                    "UPDATE rag_index_jobs SET company_id = ? WHERE id = ?", 1, id);
            case "operation = 'DELETE'" -> jdbc.update(
                    "UPDATE rag_index_jobs SET operation = ? WHERE id = ?", "DELETE", id);
            default -> throw new IllegalArgumentException("지원하지 않는 테스트 변경 값: " + assignment);
        }
    }

    private RagIndexTarget target(RagSourceType type) {
        Long owner = type.isPrivate() ? 123L : null;
        Long company = switch (type) {
            case COMPANY -> SOURCE_ID;
            case JOB_POSTING -> 456L;
            case COVER_LETTER, RESUME -> null;
        };
        return new RagIndexTarget(new RagSourceSnapshot(new RagSourceKey(type, SOURCE_ID),
                owner, company, "한글 제목", "긴 본문 😀\n".repeat(12000), "revision-1"), "p".repeat(100));
    }

    private void assertInitialState(RagIndexJobEntity job) {
        assertThat(job.getSourceSequence()).isEqualTo(1L);
        assertThat(job.getStatus()).isEqualTo(RagIndexJobStatus.PENDING);
        assertThat(job.getMaxAttempts()).isEqualTo(1);
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getLockVersion()).isZero();
        assertThat(job.getCreatedAt()).isEqualTo(Instant.parse("2026-09-07T01:02:03.123456Z"));
        assertThat(job.getUpdatedAt()).isEqualTo(job.getCreatedAt());
    }

    private void assertSqlFailure(Throwable failure, int code, String constraint) {
        assertThat(failure).isInstanceOf(DataAccessException.class);
        assertThat(failure).rootCause().isInstanceOfSatisfying(SQLException.class, sql -> {
            assertThat(sql.getErrorCode()).isEqualTo(code);
            assertThat(sql.getMessage()).contains(constraint);
        });
    }

    private TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(15);
        return transaction;
    }

    private RagIndexJobEntity registerDelete(RagSourceKey key) {
        return transaction().execute(status -> service.registerDelete(key, 1));
    }

    private RagIndexJobEntity load(Long id) {
        return transaction().execute(status -> repository.findById(id).orElseThrow());
    }

    private Long sequence(RagSourceKey key) {
        return jdbc.queryForObject("""
                SELECT last_sequence FROM rag_index_sources WHERE source_type = ? AND source_id = ?
                """, Long.class, key.sourceType().name(), key.sourceId());
    }

    private Long version() {
        return jdbc.queryForObject("""
                SELECT lock_version FROM rag_index_sources WHERE source_type = ? AND source_id = ?
                """, Long.class, KEY.sourceType().name(), KEY.sourceId());
    }

    private Integer jobCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM rag_index_jobs WHERE source_id IN (?, ?)",
                Integer.class, SOURCE_ID, SOURCE_ID + 1);
    }

    private void assertNoRows() {
        assertThat(jobCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rag_index_sources WHERE source_id IN (?, ?)",
                Integer.class, SOURCE_ID, SOURCE_ID + 1)).isZero();
    }
}
