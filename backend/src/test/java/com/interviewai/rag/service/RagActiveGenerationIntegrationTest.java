package com.interviewai.rag.service;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.index.RagIndexTarget;
import com.interviewai.rag.repository.RagIndexJobExecutionRepository;
import com.interviewai.rag.service.RagIndexJobExecutionService.ClaimedJob;
import com.interviewai.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({RagIndexSequenceService.class, RagIndexJobRegistrationService.class,
        RagIndexJobExecutionRepository.class, RagIndexJobExecutionService.class,
        RagActiveGenerationIntegrationTest.TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RagActiveGenerationIntegrationTest extends MySqlIntegrationTest {

    private static final long SOURCE_ID = 990001L;
    private static final RagSourceKey KEY = new RagSourceKey(RagSourceType.COMPANY, SOURCE_ID);

    @Autowired private RagIndexSequenceService sequenceService;
    @Autowired private RagIndexJobRegistrationService registration;
    @Autowired private RagIndexJobExecutionService execution;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @BeforeEach
    @AfterEach
    void clean() {
        dropTestGenerationIndex();
        jdbc.update("DELETE FROM rag_index_jobs WHERE source_id IN (?, ?)", SOURCE_ID, SOURCE_ID + 1);
        jdbc.update("DELETE FROM rag_index_sources WHERE source_id IN (?, ?)", SOURCE_ID, SOURCE_ID + 1);
    }

    @Test
    void publishesFirstGenerationAndRetainsItWhileReplacementIsPendingOrFailed() {
        ClaimedJob first = claimUpsert();
        assertThat(execution.isActiveGeneration(KEY, first.generationId())).isFalse();
        assertThat(complete(first)).isTrue();
        assertActive(first);
        assertThat(sourceNumber(SourceNumberColumn.LOCK_VERSION)).isEqualTo(2);

        ClaimedJob second = claimUpsert();
        assertActive(first);
        assertThat(execution.fail(second.jobId(), second.attemptId(), "PROCESSING_FAILED")).isTrue();
        assertActive(first);
        assertThat(execution.isActiveGeneration(KEY, second.generationId())).isFalse();
    }

    @Test
    void latestCompletionWinsWhenOlderJobFinishesLater() {
        ClaimedJob first = claimUpsert();
        ClaimedJob second = claimUpsert();
        assertThat(complete(second)).isTrue();
        assertThat(complete(first)).isTrue();
        assertActive(second);
        assertThat(execution.isActiveGeneration(KEY, first.generationId())).isFalse();
        assertThat(status(first.jobId())).isEqualTo("SUCCEEDED");
    }

    @Test
    void newerRegistrationAlonePreventsOldPublication() {
        ClaimedJob first = claimUpsert();
        registerUpsert();
        assertThat(complete(first)).isTrue();
        assertInactive(0);
    }

    @Test
    void deleteImmediatelyInvalidatesAndLateUpsertCannotResurrect() {
        ClaimedJob first = claimUpsert();
        complete(first);
        ClaimedJob inFlight = claimUpsert();
        long deleteId = registerDelete();
        assertThat(status(deleteId)).isEqualTo("PENDING");
        assertInactive(3);
        assertThat(execution.isActiveGeneration(KEY, first.generationId())).isFalse();
        assertThat(complete(inFlight)).isTrue();
        assertInactive(3);
        assertThat(execution.isActiveGeneration(KEY, inFlight.generationId())).isFalse();
    }

    @Test
    void lateDeleteCompletionCannotEraseLaterUpsert() {
        registerDelete();
        ClaimedJob deletion = execution.claimNext().orElseThrow();
        ClaimedJob later = claimUpsert();
        assertThat(complete(later)).isTrue();
        assertThat(complete(deletion)).isTrue();
        assertActive(later);
        assertThat(sourceNumber(SourceNumberColumn.TOMBSTONE_SEQUENCE)).isEqualTo(1);
    }

    @Test
    void repeatedDeletesAdvanceTombstoneEvenWhenProcessingFails() {
        registerDelete();
        ClaimedJob first = execution.claimNext().orElseThrow();
        registerDelete();
        assertThat(execution.fail(first.jobId(), first.attemptId(), "PROCESSING_FAILED")).isTrue();
        assertInactive(2);
        ClaimedJob second = execution.claimNext().orElseThrow();
        assertThat(complete(second)).isTrue();
        assertInactive(2);
    }

    @Test
    void wrongExpiredAndReclaimedAttemptsNeverBecomeActive() {
        ClaimedJob first = claimUpsert();
        assertThat(execution.succeed(first.jobId(), UUID.randomUUID())).isFalse();
        assertInactive(0);
        jdbc.update("UPDATE rag_index_jobs SET lease_expires_at = UTC_TIMESTAMP(6) WHERE id = ?", first.jobId());
        assertThat(complete(first)).isFalse();
        assertInactive(0);
        ClaimedJob retry = execution.claimNext().orElseThrow();
        assertThat(retry.jobId()).isEqualTo(first.jobId());
        assertThat(retry.generationId()).isNotEqualTo(first.generationId());
        assertThat(retry.pointId(0)).isNotEqualTo(first.pointId(0));
        assertThat(complete(first)).isFalse();
        assertThat(complete(retry)).isTrue();
        assertActive(retry);
        long version = sourceNumber(SourceNumberColumn.LOCK_VERSION);
        assertThat(complete(first)).isFalse();
        assertThat(complete(retry)).isFalse();
        assertActive(retry);
        assertThat(sourceNumber(SourceNumberColumn.LOCK_VERSION)).isEqualTo(version);
    }

    @Test
    void lookupSeparatesSourceTypeIdAndGeneration() {
        ClaimedJob job = claimUpsert();
        complete(job);
        assertActive(job);
        assertThat(execution.isActiveGeneration(KEY, UUID.randomUUID())).isFalse();
        assertThat(execution.isActiveGeneration(
                new RagSourceKey(RagSourceType.COMPANY, SOURCE_ID + 1), job.generationId())).isFalse();
        assertThat(execution.isActiveGeneration(
                new RagSourceKey(RagSourceType.RESUME, SOURCE_ID), job.generationId())).isFalse();
    }

    @Test
    void deleteAndUpsertInSameTransactionPreserveTombstoneAndAllocateDistinctSequences() {
        transaction().executeWithoutResult(tx -> {
            registration.registerDelete(KEY, 3);
            registration.registerUpsert(target(), 3);
        });
        assertInactive(1);
        assertThat(sourceNumber(SourceNumberColumn.LAST_SEQUENCE)).isEqualTo(2);
        ClaimedJob deletion = execution.claimNext().orElseThrow();
        ClaimedJob upsert = execution.claimNext().orElseThrow();
        assertThat(upsert.sourceSequence()).isEqualTo(2);
        complete(upsert);
        complete(deletion);
        assertActive(upsert);
    }

    @Test
    void flushedDeleteRollbackRestoresPublicationSequenceAndJobCount() {
        ClaimedJob first = claimUpsert();
        complete(first);
        long version = sourceNumber(SourceNumberColumn.LOCK_VERSION);
        transaction().executeWithoutResult(tx -> {
            registration.registerDelete(KEY, 3);
            entityManager.flush();
            assertInactive(2);
            tx.setRollbackOnly();
        });
        assertActive(first);
        assertThat(sourceNumber(SourceNumberColumn.LAST_SEQUENCE)).isEqualTo(1);
        assertThat(sourceNumber(SourceNumberColumn.TOMBSTONE_SEQUENCE)).isZero();
        assertThat(sourceNumber(SourceNumberColumn.LOCK_VERSION)).isEqualTo(version);
        assertThat(jobCount()).isEqualTo(1);
    }

    @Test
    void failedDeleteInsertRollsBackTombstoneAndSequence() {
        ClaimedJob first = claimUpsert();
        complete(first);
        // Reserve the next job sequence without changing the source counter to force a real unique violation.
        jdbc.update("""
                INSERT INTO rag_index_jobs
                    (source_type, source_id, source_sequence, operation, status,
                     max_attempts, attempt_count, created_at, updated_at, lock_version)
                VALUES ('COMPANY', ?, 2, 'DELETE', 'PENDING', 3, 0,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, SOURCE_ID);
        assertThatThrownBy(this::registerDelete).isInstanceOf(DataAccessException.class);
        assertActive(first);
        assertThat(sourceNumber(SourceNumberColumn.LAST_SEQUENCE)).isEqualTo(1);
        assertThat(sourceNumber(SourceNumberColumn.TOMBSTONE_SEQUENCE)).isZero();
        assertThat(jobCount()).isEqualTo(2);
    }

    @Test
    void sourceUpdateFailureAlsoRollsBackJdbcJobSuccess() {
        ClaimedJob first = claimUpsert();
        jdbc.execute("""
                CREATE UNIQUE INDEX test_unique_active_generation
                ON rag_index_sources (active_generation_id)
                """);
        jdbc.update("""
                INSERT INTO rag_index_sources
                    (source_type, source_id, last_sequence, active_generation_id,
                     active_sequence, tombstone_sequence, lock_version)
                VALUES ('COMPANY', ?, 1, ?, 1, 0, 0)
                """, SOURCE_ID + 1, first.generationId().toString());
        try {
            assertThatThrownBy(() -> complete(first))
                    .isInstanceOf(DataAccessException.class);
            assertThat(status(first.jobId())).isEqualTo("RUNNING");
            assertInactive(0);
        } finally {
            jdbc.update("DELETE FROM rag_index_sources WHERE source_type = 'COMPANY' AND source_id = ?",
                    SOURCE_ID + 1);
            dropTestGenerationIndex();
        }
        assertThat(complete(first)).isTrue();
        assertActive(first);
    }

    @Test
    void deleteSequenceRequiresCallerTransaction() {
        assertThatThrownBy(() -> sequenceService.allocateNextForDelete(KEY))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jobCount()).isZero();
    }

    @Test
    void firstDeleteRollbackLeavesNoSourceOrJob() {
        transaction().executeWithoutResult(tx -> {
            registration.registerDelete(KEY, 3);
            entityManager.flush();
            assertInactive(1);
            tx.setRollbackOnly();
        });
        assertThat(jobCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rag_index_sources WHERE source_id = ?",
                Integer.class, SOURCE_ID)).isZero();
    }

    @Test
    void nullDeleteKeyFailsWithoutCreatingState() {
        assertThatNullPointerException().isThrownBy(() -> transaction().executeWithoutResult(
                tx -> sequenceService.allocateNextForDelete(null)));
        assertThat(jobCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(InvalidGenerationState.class)
    void databaseRejectsInvalidGenerationState(InvalidGenerationState state) {
        registerUpsert();
        assertThatThrownBy(() -> updateInvalidGenerationState(state))
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("chk_rag_index_sources_");
        assertInactive(0);
    }

    @Test
    void concurrentDeleteRegistrationAndCompletionAlwaysEndTombstoned() throws Exception {
        ClaimedJob job = claimUpsert();
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var completion = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return complete(job);
            });
            var deletion = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return registerDelete();
            });
            assertThat(completion.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(deletion.get(15, TimeUnit.SECONDS)).isPositive();
        }
        assertInactive(2);
        assertThat(execution.isActiveGeneration(KEY, job.generationId())).isFalse();
    }

    @Test
    void completionAfterUncommittedDeleteSeesCommittedFence() throws Exception {
        ClaimedJob job = claimUpsert();
        var registered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var completing = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletion = executor.submit(() -> transaction().executeWithoutResult(tx -> {
                registration.registerDelete(KEY, 3);
                entityManager.flush();
                registered.countDown();
                await(release);
            }));
            try {
                assertThat(registered.await(10, TimeUnit.SECONDS)).isTrue();
                var completion = executor.submit(() -> {
                    completing.countDown();
                    return complete(job);
                });
                assertThat(completing.await(10, TimeUnit.SECONDS)).isTrue();
                release.countDown();
                deletion.get(15, TimeUnit.SECONDS);
                assertThat(completion.get(15, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
        }
        assertInactive(2);
    }

    @Test
    void leaseExpiredBeforeSourceLockReleaseCannotPublish() throws Exception {
        ClaimedJob job = claimUpsert();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var completing = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> transaction().executeWithoutResult(tx -> {
                jdbc.queryForObject("""
                        SELECT id FROM rag_index_sources
                        WHERE source_type = 'COMPANY' AND source_id = ? FOR UPDATE
                        """, Long.class, SOURCE_ID);
                locked.countDown();
                await(release);
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var completion = executor.submit(() -> {
                    completing.countDown();
                    return complete(job);
                });
                assertThat(completing.await(10, TimeUnit.SECONDS)).isTrue();
                jdbc.update("UPDATE rag_index_jobs SET lease_expires_at = UTC_TIMESTAMP(6) WHERE id = ?",
                        job.jobId());
                release.countDown();
                holder.get(15, TimeUnit.SECONDS);
                assertThat(completion.get(15, TimeUnit.SECONDS)).isFalse();
            } finally {
                release.countDown();
            }
        }
        assertThat(status(job.jobId())).isEqualTo("RUNNING");
        assertInactive(0);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 신호 timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void dropTestGenerationIndex() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'rag_index_sources'
                  AND index_name = 'test_unique_active_generation'
                """, Integer.class);
        if (count != null && count > 0) {
            //noinspection SqlResolve
            jdbc.execute("DROP INDEX test_unique_active_generation ON rag_index_sources");
        }
    }

    private RagIndexTarget target() {
        return new RagIndexTarget(new RagSourceSnapshot(KEY, null, SOURCE_ID,
                "기업 제목", "기업 본문", "revision-1"), "rag-v1");
    }

    private void registerUpsert() {
        transaction().executeWithoutResult(tx -> registration.registerUpsert(target(), 3));
    }

    private long registerDelete() {
        return transaction().execute(tx -> registration.registerDelete(KEY, 3).getId());
    }

    private ClaimedJob claimUpsert() {
        registerUpsert();
        return execution.claimNext().orElseThrow();
    }

    private boolean complete(ClaimedJob job) {
        return execution.succeed(job.jobId(), job.attemptId());
    }

    private void assertActive(ClaimedJob job) {
        assertThat(execution.isActiveGeneration(KEY, job.generationId())).isTrue();
        assertThat(sourceNumber(SourceNumberColumn.ACTIVE_SEQUENCE)).isEqualTo(job.sourceSequence());
    }

    private void assertInactive(long tombstone) {
        assertThat(jdbc.queryForObject("""
                SELECT active_generation_id FROM rag_index_sources
                WHERE source_type = 'COMPANY' AND source_id = ?
                """, String.class, SOURCE_ID)).isNull();
        assertThat(sourceNumber(SourceNumberColumn.ACTIVE_SEQUENCE)).isZero();
        assertThat(sourceNumber(SourceNumberColumn.TOMBSTONE_SEQUENCE)).isEqualTo(tombstone);
    }

    private long sourceNumber(SourceNumberColumn column) {
        Long value = switch (column) {
            case LAST_SEQUENCE -> jdbc.queryForObject("""
                    SELECT last_sequence FROM rag_index_sources
                    WHERE source_type = 'COMPANY' AND source_id = ?
                    """, Long.class, SOURCE_ID);
            case ACTIVE_SEQUENCE -> jdbc.queryForObject("""
                    SELECT active_sequence FROM rag_index_sources
                    WHERE source_type = 'COMPANY' AND source_id = ?
                    """, Long.class, SOURCE_ID);
            case TOMBSTONE_SEQUENCE -> jdbc.queryForObject("""
                    SELECT tombstone_sequence FROM rag_index_sources
                    WHERE source_type = 'COMPANY' AND source_id = ?
                    """, Long.class, SOURCE_ID);
            case LOCK_VERSION -> jdbc.queryForObject("""
                    SELECT lock_version FROM rag_index_sources
                    WHERE source_type = 'COMPANY' AND source_id = ?
                    """, Long.class, SOURCE_ID);
        };

        return Objects.requireNonNull(value, "RAG 원본 숫자 컬럼 조회 결과는 null일 수 없습니다.");
    }

    private void updateInvalidGenerationState(InvalidGenerationState state) {
        switch (state) {
            case NEGATIVE_TOMBSTONE -> jdbc.update("""
                    UPDATE rag_index_sources SET tombstone_sequence = -1
                    WHERE source_type = 'COMPANY' AND source_id = ?
                    """, SOURCE_ID);
            case TOMBSTONE_AFTER_LAST -> jdbc.update("""
                    UPDATE rag_index_sources SET tombstone_sequence = 2
                    WHERE source_type = 'COMPANY' AND source_id = ?
                    """, SOURCE_ID);
            case ACTIVE_SEQUENCE_WITHOUT_GENERATION -> jdbc.update("""
                    UPDATE rag_index_sources SET active_sequence = 1
                    WHERE source_type = 'COMPANY' AND source_id = ?
                    """, SOURCE_ID);
            case GENERATION_WITHOUT_ACTIVE_SEQUENCE -> jdbc.update("""
                    UPDATE rag_index_sources
                    SET active_generation_id = '00000000-0000-0000-0000-000000000001'
                    WHERE source_type = 'COMPANY' AND source_id = ?
                    """, SOURCE_ID);
            case MALFORMED_GENERATION -> jdbc.update("""
                    UPDATE rag_index_sources
                    SET active_generation_id = 'short', active_sequence = 1
                    WHERE source_type = 'COMPANY' AND source_id = ?
                    """, SOURCE_ID);
            case ACTIVE_SEQUENCE_AFTER_LAST -> jdbc.update("""
                    UPDATE rag_index_sources
                    SET active_generation_id = '00000000-0000-0000-0000-000000000001',
                        active_sequence = 2
                    WHERE source_type = 'COMPANY' AND source_id = ?
                    """, SOURCE_ID);
            case ACTIVE_SEQUENCE_AT_TOMBSTONE -> jdbc.update("""
                    UPDATE rag_index_sources
                    SET active_generation_id = '00000000-0000-0000-0000-000000000001',
                        active_sequence = 1,
                        tombstone_sequence = 1
                    WHERE source_type = 'COMPANY' AND source_id = ?
                    """, SOURCE_ID);
        }
    }

    private int jobCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_index_jobs WHERE source_id = ?", Integer.class, SOURCE_ID);

        return Objects.requireNonNull(count, "RAG 작업 개수 조회 결과는 null일 수 없습니다.");
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM rag_index_jobs WHERE id = ?", String.class, id);
    }

    private TransactionTemplate transaction() {
        var template = new TransactionTemplate(transactionManager);
        template.setTimeout(20);
        return template;
    }

    private enum SourceNumberColumn {
        LAST_SEQUENCE,
        ACTIVE_SEQUENCE,
        TOMBSTONE_SEQUENCE,
        LOCK_VERSION
    }

    private enum InvalidGenerationState {
        NEGATIVE_TOMBSTONE,
        TOMBSTONE_AFTER_LAST,
        ACTIVE_SEQUENCE_WITHOUT_GENERATION,
        GENERATION_WITHOUT_ACTIVE_SEQUENCE,
        MALFORMED_GENERATION,
        ACTIVE_SEQUENCE_AFTER_LAST,
        ACTIVE_SEQUENCE_AT_TOMBSTONE
    }
}
