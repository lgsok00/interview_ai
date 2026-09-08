package com.interviewai.rag.service;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.index.RagIndexTarget;
import com.interviewai.rag.repository.RagIndexJobExecutionRepository;
import com.interviewai.rag.service.RagIndexJobExecutionService.ClaimedJob;
import com.interviewai.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({RagIndexSequenceService.class, RagIndexJobRegistrationService.class,
        RagIndexJobExecutionRepository.class, RagIndexJobExecutionService.class,
        RagIndexJobWorker.class, RagIndexJobExecutionServiceIntegrationTest.TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RagIndexJobExecutionServiceIntegrationTest extends MySqlIntegrationTest {

    private static final long SOURCE_ID = 980001L;
    private static final RagSourceKey KEY = new RagSourceKey(RagSourceType.COMPANY, SOURCE_ID);

    @Autowired private RagIndexJobRegistrationService registrationService;
    @Autowired private RagIndexJobExecutionService service;
    @Autowired private RagIndexJobExecutionRepository repository;
    @Autowired private RagIndexJobWorker worker;
    @Autowired private JdbcTemplate jdbc;
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
    void clearTestSources() {
        jdbc.update("DELETE FROM rag_index_jobs WHERE source_type = 'COMPANY' AND source_id = ?", SOURCE_ID);
        jdbc.update("DELETE FROM rag_index_sources WHERE source_type = 'COMPANY' AND source_id = ?", SOURCE_ID);
    }

    @Test
    void appliesV8AndRejectsRunningWithoutLease() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '8' AND success = TRUE",
                Integer.class)).isEqualTo(1);
        long id = registerDelete(3);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE rag_index_jobs SET status = 'RUNNING' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("chk_rag_index_jobs_lease");
    }

    @Test
    void claimsPersistedUpsertSnapshotAndSequence() {
        var target = new RagIndexTarget(new RagSourceSnapshot(KEY, null, SOURCE_ID,
                "등록 당시 제목", "본문\n😀", "revision-1"), "rag-v1");
        Long id = transaction().execute(tx -> registrationService.registerUpsert(target, 3).getId());
        ClaimedJob job = service.claimNext().orElseThrow();
        assertThat(job.jobId()).isEqualTo(id);
        assertThat(job.sourceKey()).isEqualTo(KEY);
        assertThat(job.sourceSequence()).isEqualTo(1L);
        assertThat(job.operation()).isEqualTo(RagIndexOperation.UPSERT);
        assertThat(job.target()).isEqualTo(target);
        assertThat(job.attemptCount()).isEqualTo(1);
        assertThat(status(job.jobId())).isEqualTo("RUNNING");
        assertThat(version(job.jobId())).isEqualTo(1L);
    }

    @Test
    void claimsDeleteWithoutOriginalAndRecordsSuccessOnce() {
        long id = registerDelete(3);
        ClaimedJob job = service.claimNext().orElseThrow();
        assertThat(job.operation()).isEqualTo(RagIndexOperation.DELETE);
        assertThat(job.target()).isNull();
        assertThat(service.succeed(id, job.attemptId())).isTrue();
        assertThat(status(id)).isEqualTo("SUCCEEDED");
        assertThat(lease(id)).isNull();
        assertThat(version(id)).isEqualTo(2L);
        assertThat(service.succeed(id, job.attemptId())).isFalse();
        assertThat(service.renew(id, job.attemptId())).isFalse();
        assertThat(service.fail(id, job.attemptId(), "ERROR")).isFalse();
        assertThat(service.claimNext()).isEmpty();
    }

    @Test
    void emptyQueueReturnsEmpty() {
        assertThat(service.claimNext()).isEmpty();
    }

    @Test
    void validLeaseCannotBeReclaimedOrChangedByWrongAttempt() {
        long id = registerDelete(3);
        service.claimNext().orElseThrow();
        UUID wrong = UUID.randomUUID();
        assertThat(service.claimNext()).isEmpty();
        assertThat(service.renew(id, wrong)).isFalse();
        assertThat(service.succeed(id, wrong)).isFalse();
        assertThat(service.fail(id, wrong, "ERROR")).isFalse();
        assertThat(status(id)).isEqualTo("RUNNING");
        assertThat(version(id)).isEqualTo(1L);
    }

    @Test
    void renewsLeaseWithoutShorteningItOrConsumingAttempt() {
        long id = registerDelete(3);
        ClaimedJob job = service.claimNext().orElseThrow();
        jdbc.update("""
                UPDATE rag_index_jobs
                SET lease_expires_at = TIMESTAMPADD(SECOND, 300, UTC_TIMESTAMP(6))
                WHERE id = ?
                """, id);
        LocalDateTime before = lease(id);
        assertThat(service.renew(id, job.attemptId())).isTrue();
        assertThat(lease(id)).isEqualTo(before);
        assertThat(attempts(id)).isEqualTo(1);
        assertThat(version(id)).isEqualTo(2L);
    }

    @Test
    void expiredLeaseRejectsEveryOldOwnerMutationBeforeReclaim() {
        long id = registerDelete(3);
        ClaimedJob job = service.claimNext().orElseThrow();
        expire(id);
        assertThat(service.renew(id, job.attemptId())).isFalse();
        assertThat(service.succeed(id, job.attemptId())).isFalse();
        assertThat(service.fail(id, job.attemptId(), "ERROR")).isFalse();
        assertThat(version(id)).isEqualTo(1L);
    }

    @Test
    void reclaimChangesAttemptAndFencesLateCompletion() {
        long id = registerDelete(3);
        ClaimedJob first = service.claimNext().orElseThrow();
        expire(id);
        ClaimedJob second = service.claimNext().orElseThrow();
        assertThat(second.jobId()).isEqualTo(id);
        assertThat(second.attemptId()).isNotEqualTo(first.attemptId());
        assertThat(second.attemptCount()).isEqualTo(2);
        assertThat(service.succeed(id, first.attemptId())).isFalse();
        assertThat(service.fail(id, first.attemptId(), "ERROR")).isFalse();
        assertThat(service.renew(id, first.attemptId())).isFalse();
        assertThat(service.succeed(id, second.attemptId())).isTrue();
    }

    @Test
    void exhaustedExpiredJobBecomesFailedThenNextPollAdvances() {
        long firstId = registerDelete(1);
        service.claimNext().orElseThrow();
        expire(firstId);
        long secondId = registerDelete(3);
        assertThat(service.claimNext()).isEmpty();
        assertThat(status(firstId)).isEqualTo("FAILED");
        assertThat(failureCode(firstId)).isEqualTo("LEASE_EXPIRED");
        assertThat(lease(firstId)).isNull();
        assertThat(attempts(firstId)).isEqualTo(1);
        assertThat(service.claimNext().orElseThrow().jobId()).isEqualTo(secondId);
    }

    @Test
    void failureWaitsBeforeRetryAndFinalAttemptIsTerminal() {
        long id = registerDelete(2);
        ClaimedJob first = service.claimNext().orElseThrow();
        assertThat(service.fail(id, first.attemptId(), "PROCESSING_FAILED")).isTrue();
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(failureCode(id)).isEqualTo("PROCESSING_FAILED");
        assertThat(lease(id)).isNull();
        assertThat(service.claimNext()).isEmpty();
        jdbc.update("UPDATE rag_index_jobs SET available_at = UTC_TIMESTAMP(6) WHERE id = ?", id);
        ClaimedJob second = service.claimNext().orElseThrow();
        assertThat(second.attemptId()).isNotEqualTo(first.attemptId());
        assertThat(second.attemptCount()).isEqualTo(2);
        assertThat(failureCode(id)).isNull();
        assertThat(service.fail(id, second.attemptId(), "PROCESSING_FAILED")).isTrue();
        assertThat(status(id)).isEqualTo("FAILED");
        assertThat(service.claimNext()).isEmpty();
        assertThat(attempts(id)).isEqualTo(2);
    }

    @Test
    void exhaustedPendingJobIsNotExecuted() {
        long id = registerDelete(1);
        jdbc.update("UPDATE rag_index_jobs SET attempt_count = max_attempts WHERE id = ?", id);
        assertThat(service.claimNext()).isEmpty();
        assertThat(status(id)).isEqualTo("FAILED");
        assertThat(failureCode(id)).isEqualTo("ATTEMPTS_EXHAUSTED");
    }

    @Test
    void futureRetryDoesNotBlockAnotherJob() {
        long delayed = registerDelete(3);
        jdbc.update("""
                UPDATE rag_index_jobs
                SET available_at = TIMESTAMPADD(DAY, 1, UTC_TIMESTAMP(6)) WHERE id = ?
                """, delayed);
        long ready = registerDelete(3);
        assertThat(service.claimNext().orElseThrow().jobId()).isEqualTo(ready);
        assertThat(attempts(delayed)).isZero();
    }

    @Test
    void unknownJobMutationReturnsFalse() {
        UUID attempt = UUID.randomUUID();
        assertThat(service.renew(Long.MAX_VALUE, attempt)).isFalse();
        assertThat(service.succeed(Long.MAX_VALUE, attempt)).isFalse();
        assertThat(service.fail(Long.MAX_VALUE, attempt, "ERROR")).isFalse();
    }

    @Test
    void repositoryRequiresTransaction() {
        assertThatThrownBy(repository::findClaimableIdLocked)
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void rolledBackClaimRestoresPendingAndAttemptCount() {
        long id = registerDelete(3);
        transaction().executeWithoutResult(tx -> {
            assertThat(repository.findClaimableIdLocked()).contains(id);
            assertThat(repository.claim(id, UUID.randomUUID(), 60)).isEqualTo(1);
            tx.setRollbackOnly();
        });
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(attempts(id)).isZero();
        assertThat(version(id)).isZero();
        assertThat(service.claimNext().orElseThrow().attemptCount()).isEqualTo(1);
    }

    @Test
    void concurrentWorkersClaimSameJobOnlyOnce() throws Exception {
        long id = registerDelete(3);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return service.claimNext(); });
            var second = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return service.claimNext(); });
            Optional<ClaimedJob> a = first.get(15, TimeUnit.SECONDS);
            Optional<ClaimedJob> b = second.get(15, TimeUnit.SECONDS);
            assertThat((a.isPresent() ? 1 : 0) + (b.isPresent() ? 1 : 0)).isEqualTo(1);
            assertThat(a.or(() -> b).orElseThrow().jobId()).isEqualTo(id);
            assertThat(attempts(id)).isEqualTo(1);
        }
    }

    @Test
    void skipsLockedJobWithoutWaitingForItsTransaction() throws Exception {
        long lockedId = registerDelete(3);
        long readyId = registerDelete(3);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> transaction().executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM rag_index_jobs WHERE id = ? FOR UPDATE", Long.class, lockedId);
                locked.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("잠금 해제 신호 timeout");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var claimant = executor.submit(service::claimNext);
                assertThat(claimant.get(5, TimeUnit.SECONDS).orElseThrow().jobId()).isEqualTo(readyId);
            } finally {
                release.countDown();
            }
            holder.get(10, TimeUnit.SECONDS);
        }
        assertThat(service.claimNext().orElseThrow().jobId()).isEqualTo(lockedId);
    }

    @Test
    void workerRunsOutsideCallerTransactionAndCompletionSurvivesCallerRollback() {
        long id = registerDelete(3);
        transaction().executeWithoutResult(tx -> {
            assertThat(worker.runOnce((job, renew) -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(status(id)).isEqualTo("RUNNING");
                assertThat(renew.getAsBoolean()).isTrue();
            })).isEqualTo(RagIndexJobWorker.RunResult.SUCCEEDED);
            tx.setRollbackOnly();
        });
        assertThat(status(id)).isEqualTo("SUCCEEDED");
    }

    @Test
    void workerPersistsProcessorFailureThroughRealService() {
        long id = registerDelete(1);
        assertThat(worker.runOnce((job, renew) -> {
            throw new IllegalStateException("민감한 본문");
        })).isEqualTo(RagIndexJobWorker.RunResult.FAILURE_RECORDED);
        assertThat(status(id)).isEqualTo("FAILED");
        assertThat(failureCode(id)).isEqualTo("PROCESSING_FAILED");
    }

    private long registerDelete(int maxAttempts) {
        return transaction().execute(tx -> registrationService.registerDelete(KEY, maxAttempts).getId());
    }

    private TransactionTemplate transaction() {
        var template = new TransactionTemplate(transactionManager);
        template.setTimeout(20);
        return template;
    }

    private void expire(long id) {
        jdbc.update("UPDATE rag_index_jobs SET lease_expires_at = UTC_TIMESTAMP(6) WHERE id = ?", id);
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM rag_index_jobs WHERE id = ?", String.class, id);
    }

    private String failureCode(long id) {
        return jdbc.queryForObject("SELECT failure_code FROM rag_index_jobs WHERE id = ?", String.class, id);
    }

    private LocalDateTime lease(long id) {
        return jdbc.queryForObject("SELECT lease_expires_at FROM rag_index_jobs WHERE id = ?", LocalDateTime.class, id);
    }

    private Integer attempts(long id) {
        return jdbc.queryForObject("SELECT attempt_count FROM rag_index_jobs WHERE id = ?", Integer.class, id);
    }

    private Long version(long id) {
        return jdbc.queryForObject("SELECT lock_version FROM rag_index_jobs WHERE id = ?", Long.class, id);
    }
}
