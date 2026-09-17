package com.interviewai.rag.service;

import com.interviewai.auth.service.RefreshTokenService;
import com.interviewai.company.entity.Company;
import com.interviewai.company.repository.CompanyRepository;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.coverletter.repository.CoverLetterVersionRepository;
import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.enums.EmploymentType;
import com.interviewai.jobposting.repository.JobPostingRepository;
import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceSnapshotFactory;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.dto.AdminRagResponse;
import com.interviewai.rag.index.RagIndexJobStatus;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.repository.AdminRagRepository;
import com.interviewai.rag.repository.RagIndexJobExecutionRepository;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.repository.ResumeRepository;
import com.interviewai.resume.storage.ResumeFileStorage;
import com.interviewai.resume.storage.ResumeFileTransactionCleanup;
import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.repository.UserRepository;
import com.interviewai.user.service.UserService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({AdminRagService.class, AdminRagReindexService.class, AdminRagRepository.class,
        AdminAuthorizationService.class, RagSourceSnapshotFactory.class,
        RagIndexSequenceService.class, RagIndexJobRegistrationService.class,
        RagIndexJobExecutionRepository.class, RagIndexJobExecutionService.class,
        UserService.class, RagSourceChangeRegistrationService.class, ResumeFileTransactionCleanup.class,
        AdminRagIntegrationTest.TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminRagIntegrationTest extends MySqlIntegrationTest {

    final List<RagSourceKey> keys = new ArrayList<>();
    @Autowired
    AdminRagService service;
    @Autowired
    AdminRagReindexService reindex;
    @Autowired
    RagIndexJobRegistrationService registration;
    @Autowired
    RagIndexJobExecutionService execution;
    @Autowired
    CompanyRepository companies;
    @Autowired
    JobPostingRepository postings;
    @Autowired
    CoverLetterRepository letters;
    @Autowired
    CoverLetterVersionRepository versions;
    @Autowired
    ResumeRepository resumes;
    @Autowired
    UserRepository users;
    @Autowired
    UserService userService;
    @MockitoBean
    PasswordEncoder passwordEncoder;
    @MockitoBean
    RefreshTokenService refreshTokenService;
    @MockitoBean
    ResumeFileStorage fileStorage;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactionManager;
    @MockitoSpyBean
    AdminRagRepository queries;
    @MockitoSpyBean
    AdminAuthorizationService authorization;
    User admin;
    User owner;
    Company company;

    @BeforeEach
    void setUp() {
        admin = User.createLocalUser(UUID.randomUUID() + "@example.com", "encoded", "관리자");
        admin.changeRole(UserRole.ADMIN);
        admin = users.saveAndFlush(admin);
        owner = users.saveAndFlush(User.createLocalUser(UUID.randomUUID() + "@example.com", "encoded", "소유자"));
        company = companies.saveAndFlush(Company.create("기업", "IT", "최초 본문", null, "서울", LocalDateTime.now()));
        track(RagSourceType.COMPANY, company.getId());
    }

    @AfterEach
    void cleanUp() {
        for (RagSourceKey key : keys) {
            jdbc.update("DELETE FROM rag_index_jobs WHERE source_type = ? AND source_id = ?",
                    key.sourceType().name(), key.sourceId());
            jdbc.update("DELETE FROM rag_index_sources WHERE source_type = ? AND source_id = ?",
                    key.sourceType().name(), key.sourceId());
        }
        if (company != null) {
            jdbc.update("DELETE FROM job_postings WHERE company_id = ?", company.getId());
            jdbc.update("DELETE FROM companies WHERE id = ?", company.getId());
        }
        if (owner != null) {
            jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
        }
        if (admin != null) {
            jdbc.update("DELETE FROM users WHERE id = ?", admin.getId());
        }
    }

    @Test
    void retryPreservesPayloadSequenceAndAttemptsAndIncrementsVersion() {
        long jobId = companyJob().id();
        fail(jobId);
        var before = payload(jobId);
        long version = number("SELECT lock_version FROM rag_index_jobs WHERE id = ?", jobId);

        var retried = service.retry(subject(), jobId);

        assertThat(retried.id()).isEqualTo(jobId);
        assertThat(retried.status()).isEqualTo(RagIndexJobStatus.PENDING);
        assertThat(retried.attemptCount()).isEqualTo(3);
        assertThat(retried.maxAttempts()).isEqualTo(6);
        assertThat(retried.manualRetryCount()).isEqualTo(1);
        assertThat(retried.failureCode()).isNull();
        assertThat(retried.availableAt()).isNotNull();
        assertThat(retried.leaseExpiresAt()).isNull();
        assertThat(payload(jobId)).isEqualTo(before);
        assertThat(number("SELECT lock_version FROM rag_index_jobs WHERE id = ?", jobId)).isEqualTo(version + 1);
        assertThat(number("SELECT COUNT(*) FROM rag_index_jobs WHERE source_type = 'COMPANY' AND source_id = ?",
                company.getId())).isEqualTo(1);
        assertThat(service.source(subject(), RagSourceType.COMPANY, company.getId()).lastSequence()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempt_id FROM rag_index_jobs WHERE id = ?", String.class, jobId)).isNull();
    }

    @Test
    void allowsTwoManualRetriesAndRejectsThirdWithoutChangingBudget() {
        long jobId = companyJob().id();
        for (int retry = 1; retry <= 2; retry++) {
            fail(jobId);
            var result = service.retry(subject(), jobId);
            assertThat(result.manualRetryCount()).isEqualTo(retry);
            assertThat(result.maxAttempts()).isEqualTo(3 + retry * 3);
            assertThat(result.attemptCount()).isEqualTo(retry * 3);
        }
        fail(jobId);
        var before = service.job(subject(), jobId);
        assertCode(() -> service.retry(subject(), jobId), "RAG_JOB_RETRY_LIMIT_EXCEEDED");
        assertThat(service.job(subject(), jobId)).isEqualTo(before);
    }

    @Test
    void rejectsSupersededUpsertButCanRetryOldDeleteAfterNewUpsert() {
        long oldUpsert = companyJob().id();
        fail(oldUpsert);
        long deletion = tx().execute(status -> registration.registerDelete(companyKey(), 3).getId());
        fail(deletion);
        companyJob();

        assertCode(() -> service.retry(subject(), oldUpsert), "RAG_JOB_SUPERSEDED");
        var retried = service.retry(subject(), deletion);
        assertThat(retried.operation()).isEqualTo(RagIndexOperation.DELETE);
        assertThat(retried.sourceSequence()).isEqualTo(2);
        assertThat(retried.status()).isEqualTo(RagIndexJobStatus.PENDING);
        assertThat(service.source(subject(), RagSourceType.COMPANY, company.getId()).lastSequence()).isEqualTo(3);
    }

    @Test
    void deletedSourceRetainsRegistryAndDeleteRetryButCannotBeReindexed() {
        companyJob();
        long deletion = tx().execute(status -> {
            var locked = companies.findLockedById(company.getId()).orElseThrow();
            long id = registration.registerDelete(companyKey(), 3).getId();
            companies.delete(locked);
            return id;
        });
        fail(deletion);
        var source = service.source(subject(), RagSourceType.COMPANY, company.getId());
        assertThat(source.tombstoneSequence()).isEqualTo(2);
        assertThat(source.activeGenerationId()).isNull();
        assertThat(service.retry(subject(), deletion).status()).isEqualTo(RagIndexJobStatus.PENDING);
        assertCode(this::companyJob, "RAG_SOURCE_NOT_FOUND");
    }

    @Test
    void retryInvalidatesOldAttemptAndNewClaimPublishesNewGeneration() {
        long id = companyJob().id();
        var oldAttempt = execution.claimNext().orElseThrow();
        assertThat(oldAttempt.jobId()).isEqualTo(id);
        fail(id);
        service.retry(subject(), id);

        assertThat(execution.succeed(id, oldAttempt.attemptId())).isFalse();
        var next = execution.claimNext().orElseThrow();
        assertThat(next.jobId()).isEqualTo(id);
        assertThat(next.attemptCount()).isEqualTo(4);
        assertThat(next.attemptId()).isNotEqualTo(oldAttempt.attemptId());
        assertThat(execution.fail(id, oldAttempt.attemptId(), "LATE_FAILURE")).isFalse();
        assertThat(execution.renew(id, oldAttempt.attemptId())).isFalse();
        assertThat(execution.succeed(id, next.attemptId())).isTrue();
        assertThat(execution.isActiveGeneration(companyKey(), next.generationId())).isTrue();
    }

    @Test
    void reindexUsesCurrentContentAndRetainsActiveGenerationUntilSuccess() {
        var first = companyJob();
        var active = execution.claimNext().orElseThrow();
        assertThat(execution.succeed(first.id(), active.attemptId())).isTrue();
        tx().executeWithoutResult(status -> companies.findLockedById(company.getId()).orElseThrow()
                .update("변경 기업", "IT", "최신 본문", null, "서울", LocalDateTime.now()));

        var second = companyJob();
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.sourceSequence()).isEqualTo(first.sourceSequence() + 1);
        assertThat(payload(second.id()).get("snapshot_content").toString()).contains("최신 본문").doesNotContain("최초 본문");
        assertThat(execution.isActiveGeneration(companyKey(), active.generationId())).isTrue();
        var replacement = execution.claimNext().orElseThrow();
        assertThat(execution.succeed(second.id(), replacement.attemptId())).isTrue();
        assertThat(execution.isActiveGeneration(companyKey(), active.generationId())).isFalse();
        assertThat(execution.isActiveGeneration(companyKey(), replacement.generationId())).isTrue();
    }

    @Test
    void reindexesPostingCurrentLetterVersionAndReadyResume() {
        var posting = postings.saveAndFlush(JobPosting.create(company, "공고", "백엔드", EmploymentType.FULL_TIME,
                "서울", "채용 내용", null, null, null, false, LocalDateTime.now()));
        track(RagSourceType.JOB_POSTING, posting.getId());
        var letter = createLetter();
        tx().executeWithoutResult(status -> {
            var locked = letters.findOwnedForUpdate(letter.getId(), owner.getId()).orElseThrow();
            int version = locked.addVersion("최신 제목");
            versions.saveAndFlush(CoverLetterVersion.create(locked, version, "최신 제목", "현재 버전 본문"));
        });
        var resume = createResume(true);

        long postingJob = reindex.reindex(subject(), RagSourceType.JOB_POSTING, posting.getId()).id();
        long letterJob = reindex.reindex(subject(), RagSourceType.COVER_LETTER, letter.getId()).id();
        long resumeJob = reindex.reindex(subject(), RagSourceType.RESUME, resume.getId()).id();

        assertThat(payload(postingJob).get("snapshot_content").toString()).contains("채용 내용", "기업");
        assertThat(payload(letterJob).get("snapshot_content").toString()).contains("버전: 2", "현재 버전 본문").doesNotContain("이전 본문");
        assertThat(payload(resumeJob).get("snapshot_content").toString()).contains("추출 본문");
        assertThat(number("SELECT owner_user_id FROM rag_index_jobs WHERE id = ?", letterJob)).isEqualTo(owner.getId());
        assertThat(number("SELECT owner_user_id FROM rag_index_jobs WHERE id = ?", resumeJob)).isEqualTo(owner.getId());
    }

    @Test
    void unavailableResumeDoesNotCreateRegistryOrJob() {
        var resume = createResume(false);
        assertCode(() -> reindex.reindex(subject(), RagSourceType.RESUME, resume.getId()), "RAG_SOURCE_NOT_READY");
        assertThat(number("SELECT COUNT(*) FROM rag_index_sources WHERE source_type = 'RESUME' AND source_id = ?",
                resume.getId())).isZero();
        assertThat(number("SELECT COUNT(*) FROM rag_index_jobs WHERE source_type = 'RESUME' AND source_id = ?",
                resume.getId())).isZero();
    }

    @Test
    void filtersAndPagesHaveStableOrderingAndUtcExecutionTimes() {
        var first = companyJob();
        var second = companyJob();
        fail(second.id());
        jdbc.update("UPDATE rag_index_jobs SET created_at = '2026-01-01 00:00:00.123456' WHERE id IN (?, ?)",
                first.id(), second.id());
        var page0 = service.jobs(subject(), RagSourceType.COMPANY, company.getId(), null, null, 0, 1);
        var page1 = service.jobs(subject(), RagSourceType.COMPANY, company.getId(), null, null, 1, 1);
        assertThat(page0.items()).extracting(AdminRagResponse.Job::id).containsExactly(second.id());
        assertThat(page1.items()).extracting(AdminRagResponse.Job::id).containsExactly(first.id());
        assertThat(page0.totalElements()).isEqualTo(2);
        assertThat(page0.totalPages()).isEqualTo(2);
        assertThat(page0.items().getFirst().createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00.123456Z"));
        assertThat(page0.items().getFirst().failureCode()).isEqualTo("PROCESSING_FAILED");
        assertThat(service.jobs(subject(), RagSourceType.COMPANY, company.getId(), RagIndexJobStatus.FAILED,
                RagIndexOperation.UPSERT, 0, 20).items()).extracting(AdminRagResponse.Job::id).containsExactly(second.id());
        assertThat(service.jobs(subject(), RagSourceType.COMPANY, company.getId(), null,
                RagIndexOperation.DELETE, 0, 20).totalElements()).isZero();
        assertThat(service.jobs(subject(), null, null, null, null, Integer.MAX_VALUE, 100).items()).isEmpty();
        assertThat(service.sources(subject(), RagSourceType.COMPANY, company.getId(), 0, 20).items())
                .extracting(AdminRagResponse.Source::sourceId).containsExactly(company.getId());
    }

    @Test
    void sourcePaginationIncludesAllTypesInRegistryIdOrder() {
        companyJob();
        var resume = createResume(true);
        reindex.reindex(subject(), RagSourceType.RESUME, resume.getId());
        var first = service.sources(subject(), null, null, 0, 1);
        var second = service.sources(subject(), null, null, 1, 1);
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items().getFirst().sourceType()).isEqualTo(RagSourceType.RESUME);
        assertThat(second.items().getFirst().sourceType()).isEqualTo(RagSourceType.COMPANY);
        assertThat(first.items().getFirst().id()).isGreaterThan(second.items().getFirst().id());
    }

    @Test
    void migrationEnforcesRetryBoundsAndIntegerOverflowIsRejected() {
        long id = companyJob().id();
        assertThat(service.job(subject(), id).manualRetryCount()).isZero();
        for (int count : List.of(-1, 3)) {
            assertThatThrownBy(() -> jdbc.update("UPDATE rag_index_jobs SET manual_retry_count = ? WHERE id = ?", count, id))
                    .isInstanceOf(DataAccessException.class);
        }
        fail(id);
        jdbc.update("UPDATE rag_index_jobs SET max_attempts = 2147483647 WHERE id = ?", id);
        assertCode(() -> service.retry(subject(), id), "RAG_JOB_RETRY_CONFLICT");
        assertThat(service.job(subject(), id).maxAttempts()).isEqualTo(Integer.MAX_VALUE);
        assertThat(service.job(subject(), id).manualRetryCount()).isZero();
    }

    @Test
    void registrationAndSequenceRollbackTogetherOnResponseReadFailure() {
        var first = companyJob();
        // Stub the target spy; invoking the MANDATORY proxy during stubbing requires a transaction.
        AdminRagRepository querySpy = AopTestUtils.getUltimateTargetObject(queries);
        doThrow(new IllegalStateException("forced read failure")).when(querySpy).findJob(anyLong());
        assertThatThrownBy(this::companyJob).isInstanceOf(IllegalStateException.class).hasMessage("forced read failure");
        assertThat(number("SELECT last_sequence FROM rag_index_sources WHERE source_type = 'COMPANY' AND source_id = ?",
                company.getId())).isEqualTo(first.sourceSequence());
        assertThat(number("SELECT COUNT(*) FROM rag_index_jobs WHERE source_type = 'COMPANY' AND source_id = ?",
                company.getId())).isEqualTo(1);
    }

    @Test
    void retryBudgetAndStatusRollbackWhenFinalReadFails() {
        long id = companyJob().id();
        fail(id);
        var before = service.job(subject(), id);
        AtomicInteger reads = new AtomicInteger();
        AdminRagRepository querySpy = AopTestUtils.getUltimateTargetObject(queries);
        doAnswer(invocation -> {
            if (reads.incrementAndGet() == 3) {
                throw new IllegalStateException("forced retry response failure");
            }
            return invocation.callRealMethod();
        }).when(querySpy).findJob(id);

        assertThatThrownBy(() -> service.retry(subject(), id)).isInstanceOf(IllegalStateException.class)
                .hasMessage("forced retry response failure");
        assertThat(service.job(subject(), id)).isEqualTo(before);
    }

    @Test
    void realAuthorizationRejectsEveryOperationForOrdinaryUser() {
        String subject = owner.getId().toString();
        List<Runnable> operations = List.of(
                () -> service.sources(subject, null, null, 0, 20),
                () -> service.source(subject, RagSourceType.COMPANY, company.getId()),
                () -> service.jobs(subject, null, null, null, null, 0, 20),
                () -> service.job(subject, 1L),
                () -> service.retry(subject, 1L),
                () -> reindex.reindex(subject, RagSourceType.COMPANY, company.getId())
        );
        operations.forEach(operation -> assertCode(operation, "FORBIDDEN"));
    }

    @Test
    void concurrentRetryAcceptsExactlyOneRequest() throws Exception {
        long id = companyJob().id();
        fail(id);
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object user = invocation.callRealMethod();
            barrier.await(10, TimeUnit.SECONDS);
            return user;
        }).when(authorization).requireAdmin(anyString());

        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> attempt = () -> {
                try {
                    service.retry(subject(), id);
                    return "OK";
                } catch (CatalogException exception) {
                    return exception.getCode();
                }
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "RAG_JOB_NOT_FAILED");
        }
        assertThat(number("SELECT manual_retry_count FROM rag_index_jobs WHERE id = ?", id)).isEqualTo(1);
        assertThat(number("SELECT max_attempts FROM rag_index_jobs WHERE id = ?", id)).isEqualTo(6);
    }

    @Test
    void concurrentFirstReindexesAllocateDistinctSequentialJobs() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object user = invocation.callRealMethod();
            barrier.await(10, TimeUnit.SECONDS);
            return user;
        }).when(authorization).requireAdmin(anyString());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(this::companyJob);
            var second = pool.submit(this::companyJob);
            var firstResult = first.get(20, TimeUnit.SECONDS);
            var secondResult = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(firstResult.sourceSequence(), secondResult.sourceSequence()))
                    .containsExactlyInAnyOrder(1L, 2L);
            assertThat(firstResult.id()).isNotEqualTo(secondResult.id());
        }
        assertThat(number("SELECT last_sequence FROM rag_index_sources WHERE source_type = 'COMPANY' AND source_id = ?",
                company.getId())).isEqualTo(2);
    }

    @Test
    void reindexWaitsForCompanyUpdateAndReadsNewlyCommittedContent() throws Exception {
        var result = whileLocked(() -> companies.findLockedById(company.getId()).orElseThrow(),
                () -> companies.findLockedById(company.getId()).orElseThrow()
                        .update("새 기업", "IT", "잠금 후 최신 본문", null, "서울", LocalDateTime.now()), this::companyJob);
        assertThat(payload(result.id()).get("snapshot_content").toString()).contains("잠금 후 최신 본문").doesNotContain("최초 본문");
    }

    @Test
    void reindexWaitsForLetterUpdateAndReadsNewlyCommittedVersion() throws Exception {
        var letter = createLetter();
        var result = whileLocked(
                () -> letters.findOwnedForUpdate(letter.getId(), owner.getId()).orElseThrow(),
                () -> {
                    var locked = letters.findOwnedForUpdate(letter.getId(), owner.getId()).orElseThrow();
                    int version = locked.addVersion("동시 수정 제목");
                    versions.saveAndFlush(CoverLetterVersion.create(locked, version, "동시 수정 제목", "커밋된 최신 버전"));
                }, () -> reindex.reindex(subject(), RagSourceType.COVER_LETTER, letter.getId()));
        assertThat(payload(result.id()).get("snapshot_content").toString())
                .contains("버전: 2", "커밋된 최신 버전").doesNotContain("이전 본문");
    }

    @ParameterizedTest
    @EnumSource(value = RagSourceType.class, names = {"COVER_LETTER", "RESUME"})
    void reindexWaitsForOwnerDeletionAndDoesNotResurrectPrivateSource(RagSourceType type) throws Exception {
        long id = type == RagSourceType.COVER_LETTER ? createLetter().getId() : createResume(true).getId();
        String storageKey = type == RagSourceType.RESUME
                ? jdbc.queryForObject("SELECT storage_key FROM resumes WHERE id = ?", String.class, id)
                : null;
        reindex.reindex(subject(), type, id);
        String outcome = whileLocked(() -> users.findByIdForUpdate(owner.getId()).orElseThrow(),
                () -> userService.deleteCurrentUser(owner.getId().toString()), () -> {
                    try {
                        reindex.reindex(subject(), type, id);
                        return "OK";
                    } catch (CatalogException exception) {
                        return exception.getCode();
                    }
                });
        assertThat(outcome).isEqualTo("RAG_SOURCE_NOT_FOUND");
        assertThat(number("SELECT COUNT(*) FROM rag_index_jobs WHERE source_type = ? AND source_id = ?", type.name(), id)).isEqualTo(2);
        assertThat(number("SELECT tombstone_sequence FROM rag_index_sources WHERE source_type = ? AND source_id = ?", type.name(), id)).isEqualTo(2);
        assertThat(number("SELECT COUNT(*) FROM users WHERE id = ?", owner.getId())).isZero();
        assertThat(number("SELECT COUNT(*) FROM cover_letters WHERE user_id = ?", owner.getId())).isZero();
        assertThat(number("SELECT COUNT(*) FROM resumes WHERE user_id = ?", owner.getId())).isZero();
        if (storageKey != null) {
            verify(fileStorage).delete(storageKey);
        }
    }

    @Test
    void withdrawalRollbackPreservesDocumentsRegistryAndFiles() {
        var letter = createLetter();
        var resume = createResume(true);
        reindex.reindex(subject(), RagSourceType.COVER_LETTER, letter.getId());
        reindex.reindex(subject(), RagSourceType.RESUME, resume.getId());

        tx().executeWithoutResult(status -> {
            userService.deleteCurrentUser(owner.getId().toString());
            assertThat(number("SELECT COUNT(*) FROM users WHERE id = ?", owner.getId())).isZero();
            status.setRollbackOnly();
        });

        assertThat(number("SELECT COUNT(*) FROM users WHERE id = ?", owner.getId())).isEqualTo(1);
        assertThat(number("SELECT COUNT(*) FROM cover_letters WHERE id = ?", letter.getId())).isEqualTo(1);
        assertThat(number("SELECT COUNT(*) FROM resumes WHERE id = ?", resume.getId())).isEqualTo(1);
        for (RagSourceKey key : List.of(new RagSourceKey(RagSourceType.COVER_LETTER, letter.getId()),
                new RagSourceKey(RagSourceType.RESUME, resume.getId()))) {
            var source = service.source(subject(), key.sourceType(), key.sourceId());
            assertThat(source.lastSequence()).isEqualTo(1);
            assertThat(source.tombstoneSequence()).isZero();
            assertThat(number("SELECT COUNT(*) FROM rag_index_jobs WHERE source_type = ? AND source_id = ?",
                    key.sourceType().name(), key.sourceId())).isEqualTo(1);
        }
        verifyNoInteractions(fileStorage);
    }

    private <T> T whileLocked(Runnable acquire, Runnable changeBeforeCommit, Callable<T> operation) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch authorized = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object user = invocation.callRealMethod();
            authorized.countDown();
            return user;
        }).when(authorization).requireAdmin(anyString());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> tx().executeWithoutResult(status -> {
                acquire.run();
                locked.countDown();
                await(release);
                changeBeforeCommit.run();
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var waiter = pool.submit(operation);
                assertThat(authorized.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> waiter.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                holder.get(15, TimeUnit.SECONDS);
                return waiter.get(15, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
    }

    private CoverLetter createLetter() {
        CoverLetter letter = tx().execute(status -> {
            var saved = letters.saveAndFlush(CoverLetter.create(owner, "이전 제목"));
            versions.saveAndFlush(CoverLetterVersion.createInitial(saved, "이전 제목", "이전 본문"));
            return saved;
        });
        track(RagSourceType.COVER_LETTER, letter.getId());
        return letter;
    }

    private Resume createResume(boolean ready) {
        Resume resume = Resume.create(owner, "이력서", "resume.pdf", UUID.randomUUID().toString(),
                "application/pdf", 100, "a".repeat(64));
        if (ready) {
            resume.completeExtraction("추출 본문");
        }
        resume = resumes.saveAndFlush(resume);
        track(RagSourceType.RESUME, resume.getId());
        return resume;
    }

    private void track(RagSourceType type, long id) {
        keys.add(new RagSourceKey(type, id));
    }

    private AdminRagResponse.Job companyJob() {
        return reindex.reindex(subject(), RagSourceType.COMPANY, company.getId());
    }

    private RagSourceKey companyKey() {
        return new RagSourceKey(RagSourceType.COMPANY, company.getId());
    }

    private String subject() {
        return admin.getId().toString();
    }

    private void fail(long id) {
        jdbc.update("""
                UPDATE rag_index_jobs
                SET status = 'FAILED', attempt_count = max_attempts,
                    lease_expires_at = NULL, available_at = NULL,
                    failure_code = 'PROCESSING_FAILED', lock_version = lock_version + 1
                WHERE id = ?
                """, id);
    }

    private java.util.Map<String, Object> payload(long id) {
        return jdbc.queryForMap("""
                SELECT source_type, source_id, source_sequence, operation,
                       snapshot_title, snapshot_content, source_revision, pipeline_version,
                       owner_user_id, company_id, created_at
                FROM rag_index_jobs WHERE id = ?
                """, id);
    }

    private long number(String sql, Object... args) {
        Long result = jdbc.queryForObject(sql, Long.class, args);

        if (result == null) {
            throw new AssertionError("숫자 조회 결과가 NULL입니다: " + sql);
        }

        return result;
    }

    private TransactionTemplate tx() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(20);
        return template;
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CatalogException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(code));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
