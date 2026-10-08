package com.interviewai.ai.usage;

import com.interviewai.coverletter.draft.CoverLetterDraft;
import com.interviewai.coverletter.draft.CoverLetterDraftExecutionService;
import com.interviewai.coverletter.draft.CoverLetterDraftPolicy;
import com.interviewai.coverletter.draft.CoverLetterDraftProperties;
import com.interviewai.coverletter.draft.CoverLetterDraftRepository;
import com.interviewai.coverletter.draft.CoverLetterDraftService;
import com.interviewai.coverletter.draft.CoverLetterDraftSnapshotAssembler;
import com.interviewai.coverletter.draft.CoverLetterDraftStatus;
import com.interviewai.coverletter.draft.CreateCoverLetterDraftRequest;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.interview.dto.CreateInterviewSessionRequest;
import com.interviewai.interview.entity.InterviewAnswer;
import com.interviewai.interview.entity.InterviewAnswerEvaluation;
import com.interviewai.interview.entity.InterviewQuestion;
import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.QuestionGenerationSource;
import com.interviewai.interview.evaluation.AnswerEvaluationExecutionService;
import com.interviewai.interview.evaluation.AnswerEvaluationGenerator;
import com.interviewai.interview.evaluation.AnswerEvaluationPolicy;
import com.interviewai.interview.evaluation.AnswerEvaluationProperties;
import com.interviewai.interview.generation.InterviewFollowUpGenerator;
import com.interviewai.interview.generation.InterviewGenerationDeadline;
import com.interviewai.interview.generation.InterviewGenerationExecutionService;
import com.interviewai.interview.generation.InterviewGenerationPolicy;
import com.interviewai.interview.generation.InterviewGenerationProperties;
import com.interviewai.interview.repository.InterviewAnswerEvaluationRepository;
import com.interviewai.interview.repository.InterviewAnswerRepository;
import com.interviewai.interview.repository.InterviewQuestionRepository;
import com.interviewai.interview.repository.InterviewSessionRepository;
import com.interviewai.interview.service.AnswerEvaluationService;
import com.interviewai.interview.service.InterviewAnswerService;
import com.interviewai.interview.service.InterviewFollowUpService;
import com.interviewai.interview.service.InterviewSessionService;
import com.interviewai.interview.service.InterviewSessionSnapshotAssembler;
import com.interviewai.interview.service.InterviewSourceSnapshot;
import com.interviewai.rag.service.RagSourceChangeRegistrationService;
import com.interviewai.resume.storage.ResumeFileTransactionCleanup;
import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import com.interviewai.user.service.UserDeletionService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 실제 도메인 서비스와 사용량 저장소를 연결한다. 원본 조립만 격리하며 외부 AI는 호출하지 않는다.
 */
@DataJpaTest
@Import({AiUsageAdmissionService.class, AiUsageRepository.class, AiUsageIdentity.class,
        AiUsageLifecycleService.class, InterviewAnswerService.class, InterviewFollowUpService.class,
        InterviewGenerationDeadline.class, AnswerEvaluationExecutionService.class,
        CoverLetterDraftExecutionService.class, UserDeletionService.class,
        InterviewSessionService.class, InterviewGenerationExecutionService.class,
        AnswerEvaluationService.class, CoverLetterDraftService.class,
        AdminAuthorizationService.class, AiUsageGenerationAdmissionIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AiUsageGenerationAdmissionIntegrationTest extends MySqlIntegrationTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 8);
    private static final LocalDateTime NOW = DAY.atTime(10, 0);
    private static final CreateCoverLetterDraftRequest DRAFT_REQUEST =
            new CreateCoverLetterDraftRequest(20L, 30L, "강조");

    @Autowired
    InterviewSessionService sessionService;
    @Autowired
    InterviewGenerationExecutionService generation;
    @Autowired
    AnswerEvaluationService evaluationService;
    @Autowired
    CoverLetterDraftService draftService;
    @Autowired
    AiUsageIdentity identity;
    @Autowired
    UserRepository users;
    @Autowired
    CoverLetterRepository coverLetters;
    @Autowired
    CoverLetterDraftRepository drafts;
    @Autowired
    InterviewSessionRepository sessions;
    @Autowired
    InterviewQuestionRepository questions;
    @Autowired
    InterviewAnswerRepository answers;
    @Autowired
    InterviewAnswerEvaluationRepository evaluations;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager manager;
    @MockitoSpyBean
    AiUsageRepository usageRepository;
    @MockitoSpyBean
    AiUsageProperties usageProperties;
    @MockitoSpyBean
    AnswerEvaluationProperties evaluationProperties;
    @MockitoSpyBean
    CoverLetterDraftProperties draftProperties;
    @MockitoBean
    InterviewSessionSnapshotAssembler sessionSnapshots;
    @MockitoBean
    CoverLetterDraftSnapshotAssembler draftSnapshots;
    @MockitoBean
    RagSourceChangeRegistrationService rag;
    @MockitoBean
    ResumeFileTransactionCleanup fileCleanup;
    @MockitoBean
    InterviewFollowUpGenerator followUpGenerator;
    @MockitoSpyBean
    AiUsageLifecycleService lifecycle;
    @Autowired
    AnswerEvaluationExecutionService evaluationExecution;
    @Autowired
    CoverLetterDraftExecutionService draftExecution;
    @Autowired
    InterviewAnswerService answerService;
    @Autowired
    InterviewFollowUpService followUpService;
    @Autowired
    UserDeletionService deletion;

    private long userId;
    private long coverLetterId;
    private long sessionId;
    private long answerId;
    private String subjectKey;

    @BeforeEach
    void setUp() {
        cleanup();
        AiUsageRepository target = AopTestUtils.getUltimateTargetObject(usageRepository);
        doReturn(NOW).when(target).currentUtcTime();
        tx().executeWithoutResult(status -> {
            User user = users.saveAndFlush(User.createLocalUser("admission@example.com", "hash", "사용자"));
            userId = user.getId();
            coverLetterId = coverLetters.saveAndFlush(CoverLetter.create(user, "기존 제목")).getId();
            InterviewSession session = newSession(user);
            session.markReady(NOW);
            session.start(NOW);
            sessions.saveAndFlush(session);
            sessionId = session.getId();
            InterviewQuestion question = questions.saveAndFlush(InterviewQuestion.create(session, 1,
                    InterviewQuestionType.TECHNICAL, QuestionGenerationSource.AI, "질문", null, NOW));
            answerId = answers.saveAndFlush(InterviewAnswer.create(question, "저장된 답변", NOW)).getId();
        });
        subjectKey = identity.subjectKey("admission@example.com");
        when(sessionSnapshots.assemble(userId, 20L)).thenReturn(new InterviewSourceSnapshot(
                new InterviewSourceSnapshot.JobPostingSnapshot(20L, 40L, "회사", "공고", "Backend", "본문"),
                null, null));
        when(draftSnapshots.assemble(eq(userId), eq(coverLetterId), any())).thenAnswer(invocation ->
                new CoverLetterDraftSnapshotAssembler.PreparedInput(
                        coverLetters.findById(coverLetterId).orElseThrow(), input(), "a".repeat(64)));
    }

    @AfterEach
    void cleanup() {
        // Testcontainers 전용 DB. 예약은 사용자 삭제로 제거되지 않으므로 FK 순서대로 초기화한다.
        for (String table : List.of("ai_usage_reservations", "ai_usage_subject_daily",
                "ai_usage_global_daily", "ai_usage_admin_embedding_minutes", "ai_usage_subjects", "users")) {

            // noinspection SqlWithoutWhere
            jdbc.update("DELETE FROM " + table);
        }
    }

    @ParameterizedTest
    @EnumSource(Path.class)
    void commitsEachRegistrationWithCorrectFeatureAndResource(Path path) {
        long sourceId = prepare(path);
        long resourceId = invoke(path, sourceId);
        assertThat(used(path.feature)).isEqualTo(1);
        assertThat(globalUsed()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT resource_id FROM ai_usage_reservations WHERE feature = ? AND status = 'ACTIVE'",
                Long.class, path.feature.name())).containsExactly(resourceId);
        if (path == Path.INITIAL_RETRY) {
            assertThat(jdbc.queryForObject("SELECT retry_count FROM interview_generation_jobs WHERE session_id = ?",
                    Integer.class, sourceId)).isEqualTo(1);
        }
        if (path == Path.EVALUATION_RETRY) {
            assertThat(evaluationService.get(subject(), sessionId, answerId).manualRetryCount()).isEqualTo(1);
        }
        if (path == Path.DRAFT_REGENERATE) {
            assertThat(resourceId).isNotEqualTo(sourceId);
            assertThat(draftService.get(subject(), coverLetterId, resourceId).sourceDraftId()).isEqualTo(sourceId);
        }
    }

    @ParameterizedTest
    @EnumSource(Path.class)
    void globalLimitRollsBackDomainRegistrationAndRetryCounters(Path path) {
        long sourceId = prepare(path);
        jdbc.update("INSERT INTO ai_usage_global_daily (usage_date, chat_accepted) VALUES (?, 500)", DAY);
        var before = snapshot();
        rejected("AI_CAPACITY_EXCEEDED", () -> invoke(path, sourceId));
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(Path.class)
    void dailyLimitRollsBackEachRegistration(Path path) {
        long sourceId = prepare(path);
        jdbc.update("INSERT INTO ai_usage_subjects (subject_key, created_at) VALUES (?, ?)", subjectKey, NOW);
        jdbc.update("INSERT INTO ai_usage_subject_daily (subject_key, usage_date, feature, accepted) VALUES (?, ?, ?, ?)",
                subjectKey, DAY, path.feature.name(), usageProperties.dailyLimit(path.feature));
        var before = snapshot();
        rejected("AI_DAILY_LIMIT_EXCEEDED", () -> invoke(path, sourceId));
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(Path.class)
    void admissionPauseRollsBackEachRegistration(Path path) {
        long sourceId = prepare(path);
        doReturn(false).when(usageProperties).chatAdmissionEnabled();
        var before = snapshot();
        rejected("AI_CAPACITY_EXCEEDED", () -> invoke(path, sourceId));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void callerRollbackRemovesNewSessionJobCountersAndReservation() {
        var before = snapshot();
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            invoke(Path.INITIAL, 0);
            throw new IllegalStateException("registration rollback");
        })).hasMessage("registration rollback");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void snapshotFailureDoesNotCreateJobOrCharge() {
        when(sessionSnapshots.assemble(userId, 20L)).thenThrow(new IllegalArgumentException("invalid source"));
        var before = snapshot();
        assertThatThrownBy(() -> invoke(Path.INITIAL, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid source");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void sharedSlotsRejectThirdFeatureAndRollBackDraft() {
        invoke(Path.INITIAL, 0);
        invoke(Path.EVALUATION, 0);
        var before = snapshot();
        rejected("AI_PENDING_LIMIT_EXCEEDED", () -> invoke(Path.DRAFT, 0));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(globalUsed()).isEqualTo(2);
    }

    @Test
    void existingEvaluationDoesNotChargeWhenAdmissionIsPausedAndLimitsAreExhausted() {
        long id = evaluationService.request(subject(), sessionId, answerId).id();
        jdbc.update("UPDATE ai_usage_subject_daily SET accepted = 30 WHERE feature = 'ANSWER_EVALUATION'");
        doReturn(false).when(usageProperties).chatAdmissionEnabled();
        var before = snapshot();
        assertThat(evaluationService.request(subject(), sessionId, answerId).id()).isEqualTo(id);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void disabledDraftStoresFailureWithoutUsageOrReservation() {
        doReturn(false).when(draftProperties).enabled();
        var response = draftService.create(subject(), coverLetterId, DRAFT_REQUEST);
        assertThat(response.status()).isEqualTo(CoverLetterDraftStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("DRAFT_AI_NOT_CONFIGURED");
        assertThat(globalUsed()).isZero();
        assertThat(used(AiUsageFeature.COVER_LETTER_DRAFT)).isZero();
        assertThat(count("ai_usage_reservations")).isZero();
        assertThat(drafts.findById(response.id())).isPresent();
    }

    @Test
    void disabledEvaluationRejectsWithoutChangingUsage() {
        doReturn(false).when(evaluationProperties).enabled();
        var before = snapshot();
        domainRejected("ANSWER_EVALUATION_DISABLED", () -> invoke(Path.EVALUATION, 0));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void pendingDraftCannotRegenerateAndOwnerChecksDoNotCharge() {
        long id = invoke(Path.DRAFT, 0);
        var before = snapshot();
        domainRejected("COVER_LETTER_DRAFT_CONFLICT", () -> invoke(Path.DRAFT_REGENERATE, id));
        assertThat(snapshot()).isEqualTo(before);
        long otherId = Objects.requireNonNull(tx().execute(status -> users.saveAndFlush(
                User.createLocalUser("other@example.com", "hash", "다른 사용자")).getId()));
        domainRejected("INTERVIEW_SESSION_NOT_FOUND",
                () -> evaluationService.request(Long.toString(otherId), sessionId, answerId));
        domainRejected("INTERVIEW_SESSION_NOT_FOUND", () -> generation.retry(otherId, sessionId));
        assertThat(globalUsed()).isEqualTo(1);
        assertThat(count("ai_usage_reservations")).isEqualTo(1);
    }

    @Test
    void duplicateActiveReservationRollsBackManualRetry() {
        long sourceId = prepare(Path.INITIAL_RETRY);
        generation.retry(userId, sourceId);
        // 종료 연결 전 비정상 상태를 재현한다. 남은 활성 예약을 덮어쓰면 안 된다.
        failGeneration(sourceId);
        var before = snapshot();
        rejected("AI_REQUEST_IN_PROGRESS", () -> generation.retry(userId, sourceId));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void preservesBothManualRetryMaximumsWithoutCharging() {
        long initialId = prepare(Path.INITIAL_RETRY);
        jdbc.update("UPDATE interview_generation_jobs SET retry_count = 2 WHERE session_id = ?", initialId);
        prepare(Path.EVALUATION_RETRY);
        jdbc.update("UPDATE interview_answer_evaluations SET manual_retry_count = 2 WHERE answer_id = ?", answerId);
        var before = snapshot();
        domainRejected("INTERVIEW_GENERATION_CONFLICT", () -> generation.retry(userId, initialId));
        domainRejected("ANSWER_EVALUATION_RETRY_CONFLICT",
                () -> evaluationService.retry(subject(), sessionId, answerId));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void requiresNewRetryCommitsIndependentlyOfOuterRollback() {
        long id = prepare(Path.INITIAL_RETRY);
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            generation.retry(userId, id);
            throw new IllegalStateException("outer rollback");
        })).hasMessage("outer rollback");
        assertThat(jdbc.queryForObject("SELECT status FROM interview_sessions WHERE id = ?", String.class, id))
                .isEqualTo("GENERATING");
        assertThat(used(AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(1);
        assertThat(count("ai_usage_reservations")).isEqualTo(1);
    }

    @Test
    void concurrentEvaluationRequestsReturnOneEvaluationAndChargeOnce() throws Exception {
        var ids = race(() -> evaluationService.request(subject(), sessionId, answerId).id());
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        assertThat(count("interview_answer_evaluations")).isEqualTo(1);
        assertThat(used(AiUsageFeature.ANSWER_EVALUATION)).isEqualTo(1);
        assertThat(globalUsed()).isEqualTo(1);
        assertThat(count("ai_usage_reservations")).isEqualTo(1);
    }

    @Test
    void concurrentInitialRequestsCompeteForLastDailyAdmission() throws Exception {
        jdbc.update("INSERT INTO ai_usage_subjects (subject_key, created_at) VALUES (?, ?)", subjectKey, NOW);
        jdbc.update("INSERT INTO ai_usage_subject_daily (subject_key, usage_date, feature, accepted) VALUES (?, ?, 'INITIAL_QUESTIONS', 2)",
                subjectKey, DAY);
        jdbc.update("INSERT INTO ai_usage_global_daily (usage_date, chat_accepted) VALUES (?, 2)", DAY);
        var results = race(() -> {
            try {
                invoke(Path.INITIAL, 0);
                return "OK";
            } catch (AiUsageException exception) {
                return exception.getCode();
            }
        });
        assertThat(results).containsExactlyInAnyOrder("OK", "AI_DAILY_LIMIT_EXCEEDED");
        assertThat(count("interview_sessions")).isEqualTo(2); // 기존 진행 세션 + 성공한 신규 세션
        assertThat(count("interview_generation_jobs")).isEqualTo(1);
        assertThat(used(AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(3);
        assertThat(globalUsed()).isEqualTo(3);
        assertThat(count("ai_usage_reservations")).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = Path.class, names = {"INITIAL", "EVALUATION", "DRAFT"})
    void workerCompletionFinishesSlotWithoutRefund(Path path) {
        Running running = running(path);
        assertBound(running);
        assertThat(finish(running)).isTrue();
        assertThat(reservationStatus(path.feature, running.resource())).isEqualTo("FINISHED");
        assertThat(globalUsed()).isEqualTo(1);
        assertThat(finish(running)).isFalse();
        assertThat(lifecycle.recoverReservations()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Path.class, names = {"INITIAL", "EVALUATION", "DRAFT"})
    void automaticRetryRetainsSlotAndDoesNotChargeAgain(Path path) {
        Running old = running(path);
        assertThat(fail(old, true)).isTrue();
        assertThat(reservationStatus(path.feature, old.resource())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT worker_attempt_id FROM ai_usage_reservations WHERE resource_id = ? AND feature = ?",
                String.class, old.resource(), path.feature.name())).isNull();
        assertThat(lifecycle.recoverReservations()).isZero();
        due(path, old.resource());
        Running current = claim(path, old.resource());
        assertBound(current);
        assertThat(finish(old)).isFalse();
        assertThat(fail(old, false)).isFalse();
        assertThat(reservationStatus(path.feature, old.resource())).isEqualTo("ACTIVE");
        assertThat(finish(current)).isTrue();
        assertThat(globalUsed()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = Path.class, names = {"INITIAL", "EVALUATION", "DRAFT"})
    void finalFailureReleasesSlotAndManualRequestChargesAgain(Path path) {
        Running running = running(path);
        assertThat(fail(running, false)).isTrue();
        assertThat(reservationStatus(path.feature, running.resource())).isEqualTo("FINISHED");
        long nextResource = switch (path) {
            case INITIAL -> invoke(Path.INITIAL_RETRY, running.resource());
            case EVALUATION -> invoke(Path.EVALUATION_RETRY, running.resource());
            case DRAFT -> invoke(Path.DRAFT_REGENERATE, running.resource());
            default -> throw new IllegalArgumentException();
        };
        assertThat(reservationStatus(path.feature, nextResource)).isEqualTo("ACTIVE");
        assertThat(globalUsed()).isEqualTo(2);
        assertThat(count("ai_usage_reservations")).isEqualTo(2);
        assertThat(finish(running)).isFalse();
        assertThat(reservationStatus(path.feature, nextResource)).isEqualTo("ACTIVE");
    }

    @ParameterizedTest
    @EnumSource(value = Path.class, names = {"INITIAL", "EVALUATION", "DRAFT"})
    void expiredAsyncLeaseKeepsSlotUntilDomainWorkerRecovers(Path path) {
        Running old = running(path);
        jdbc.update("UPDATE " + jobTable(path) + " SET lease_expires_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) WHERE "
                + jobKey(path) + " = ?", old.resource());
        jdbc.update("UPDATE ai_usage_reservations SET lease_expires_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) WHERE feature = ?",
                path.feature.name());
        assertThat(lifecycle.recoverReservations()).isZero();
        assertThat(finish(old)).isFalse();
        if (path == Path.EVALUATION) {
            assertThat(evaluationExecution.recoverOne()).isTrue();
            due(path, old.resource());
        }
        Running current = claim(path, old.resource());
        assertBound(current);
        assertThat(finish(old)).isFalse();
        assertThat(finish(current)).isTrue();
        assertThat(globalUsed()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = Path.class, names = {"INITIAL", "EVALUATION", "DRAFT"})
    void usageWriteFailureRollsBackDomainCompletion(Path path) {
        Running running = running(path);
        var before = snapshot();
        AiUsageLifecycleService target = AopTestUtils.getUltimateTargetObject(lifecycle);
        doThrow(AiUsageException.unavailable(new IllegalStateException("storage failed")))
                .when(target).finishAsync(eq(path.feature), eq(running.resource()), any());
        rejected("AI_USAGE_UNAVAILABLE", () -> finish(running));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM " + jobTable(path) + " WHERE " + jobKey(path) + " = ?",
                String.class, running.resource())).isEqualTo(path == Path.EVALUATION ? "PROCESSING" : "RUNNING");
    }

    @Test
    void renewSynchronizesInitialReservationLease() {
        Running running = running(Path.INITIAL);
        var claim = (InterviewGenerationExecutionService.Claim) running.claim();
        assertThat(generation.renew(claim)).isTrue();
        assertBound(running);
    }

    @Test
    void exhaustedEvaluationAndDraftLeasesFinishReservations() {
        Running evaluation = running(Path.EVALUATION);
        Running draft = running(Path.DRAFT);
        for (Running job : List.of(evaluation, draft)) {
            jdbc.update("UPDATE " + jobTable(job.path())
                    + " SET attempt_count = 3, lease_expires_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) WHERE "
                    + jobKey(job.path()) + " = ?", job.resource());
        }
        assertThat(lifecycle.recoverReservations()).isZero();
        assertThat(evaluationExecution.recoverOne()).isTrue();
        assertThat(draftExecution.recoverOneExhaustedLease()).isTrue();
        assertThat(reservationStatus(Path.EVALUATION.feature, evaluation.resource())).isEqualTo("FINISHED");
        assertThat(reservationStatus(Path.DRAFT.feature, draft.resource())).isEqualTo("FINISHED");
        assertThat(globalUsed()).isEqualTo(2);
    }

    @Test
    void exhaustedInitialLeaseRecoveryBindsNewAttemptBeforeFinishing() {
        Running old = running(Path.INITIAL);
        jdbc.update("UPDATE interview_generation_jobs SET attempt_count = 3, lease_expires_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) WHERE session_id = ?",
                old.resource());
        var current = generation.claimNext().orElseThrow();
        assertThat(current.recoveryOnly()).isTrue();
        assertThat(generation.fail(current, "LEASE_EXPIRED", false)).isTrue();
        assertThat(reservationStatus(Path.INITIAL.feature, old.resource())).isEqualTo("FINISHED");
    }

    @Test
    void recoveryFinishesOldTerminalReservationButKeepsPendingJobs() {
        long initial = invoke(Path.INITIAL, 0);
        invoke(Path.EVALUATION, 0);
        assertThat(lifecycle.recoverReservations()).isZero();
        failGeneration(initial);
        assertThat(lifecycle.recoverReservations()).isEqualTo(1);
        assertThat(reservationStatus(Path.INITIAL.feature, initial)).isEqualTo("FINISHED");
        assertThat(reservationStatus(Path.EVALUATION.feature, answerId)).isEqualTo("ACTIVE");
        assertThat(lifecycle.recoverReservations()).isZero();
        assertThat(globalUsed()).isEqualTo(2);
    }

    @Test
    void recoveryCancelsMissingAsyncSource() {
        long initial = invoke(Path.INITIAL, 0);
        jdbc.update("DELETE FROM interview_sessions WHERE id = ?", initial);
        assertThat(lifecycle.recoverReservations()).isEqualTo(1);
        assertThat(reservationStatus(Path.INITIAL.feature, initial)).isEqualTo("CANCELLED");
        assertThat(globalUsed()).isEqualTo(1);
    }

    @Test
    void followUpSuccessCommitsQuestionAndFinishesReservationWithoutReplayCharge() {
        realAdmissionClock();
        when(followUpGenerator.generate(any())).thenReturn(followUpResult());
        long parent = parentId();
        var result = followUpService.generate(subject(), sessionId, parent);
        assertThat(result.parentQuestionId()).isEqualTo(parent);
        assertThat(reservationStatus(AiUsageFeature.FOLLOW_UP, parent)).isEqualTo("FINISHED");
        assertThat(totalUsage()).isEqualTo(1);
        doReturn(false).when(usageProperties).chatAdmissionEnabled();
        assertThat(followUpService.generate(subject(), sessionId, parent)).isEqualTo(result);
        assertThat(totalUsage()).isEqualTo(1);
        verify(followUpGenerator, times(1)).generate(any());
    }

    @Test
    void sameParentDuringExternalCallIsRejectedWithoutSecondModelCall() throws Exception {
        realAdmissionClock();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(followUpGenerator.generate(any())).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
            return followUpResult();
        });
        long parent = parentId();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> followUpService.generate(subject(), sessionId, parent));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                rejected("AI_REQUEST_IN_PROGRESS", () -> followUpService.generate(subject(), sessionId, parent));
                assertThat(totalUsage()).isEqualTo(1);
            } finally {
                release.countDown();
            }
            assertThat(first.get(20, TimeUnit.SECONDS).parentQuestionId()).isEqualTo(parent);
        }
        verify(followUpGenerator, times(1)).generate(any());
    }

    @Test
    void modelFailureCancelsSlotButKeepsAnswerAndCharge() {
        realAdmissionClock();
        when(followUpGenerator.generate(any())).thenThrow(new IllegalStateException("model failed"));
        long parent = parentId();
        domainRejected("INTERVIEW_FOLLOW_UP_UNAVAILABLE", () -> followUpService.generate(subject(), sessionId, parent));
        assertThat(reservationStatus(AiUsageFeature.FOLLOW_UP, parent)).isEqualTo("CANCELLED");
        assertThat(totalUsage()).isEqualTo(1);
        assertThat(answers.findById(answerId)).isPresent();
        assertThat(questions.findByParentQuestionId(parent)).isEmpty();
    }

    @Test
    void saveRollbackPreservesReservationAndAnswer() {
        realAdmissionClock();
        long parent = parentId();
        var preparation = answerService.prepareFollowUp(subject(), sessionId, parent);
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            answerService.saveFollowUp(subject(), sessionId, parent, preparation.reservationId(), followUpResult());
            throw new IllegalStateException("rollback result");
        })).hasMessage("rollback result");
        assertThat(questions.findByParentQuestionId(parent)).isEmpty();
        assertThat(reservationStatus(AiUsageFeature.FOLLOW_UP, parent)).isEqualTo("ACTIVE");
        assertThat(answers.findById(answerId)).isPresent();
        assertThat(totalUsage()).isEqualTo(1);
    }

    @Test
    void expiredFollowUpCanReAdmitAndOldTokenCannotSaveOrCancelNewReservation() {
        realAdmissionClock();
        long parent = parentId();
        var old = answerService.prepareFollowUp(subject(), sessionId, parent);
        jdbc.update("UPDATE ai_usage_reservations SET lease_expires_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) WHERE id = ?",
                old.reservationId().toString());
        var current = answerService.prepareFollowUp(subject(), sessionId, parent);
        assertThat(current.reservationId()).isNotEqualTo(old.reservationId());
        domainRejected("AI_REQUEST_EXPIRED", () -> answerService.saveFollowUp(
                subject(), sessionId, parent, old.reservationId(), followUpResult()));
        lifecycle.cancelFollowUp(old.reservationId());
        assertThat(reservationStatus(AiUsageFeature.FOLLOW_UP, parent)).isEqualTo("ACTIVE");
        assertThat(questions.findByParentQuestionId(parent)).isEmpty();
        answerService.saveFollowUp(subject(), sessionId, parent, current.reservationId(), followUpResult());
        assertThat(totalUsage()).isEqualTo(2);
    }

    @Test
    void recoveryExpiresFollowUpWithoutRefundAndIsIdempotent() {
        realAdmissionClock();
        var preparation = answerService.prepareFollowUp(subject(), sessionId, parentId());
        jdbc.update("UPDATE ai_usage_reservations SET lease_expires_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) WHERE id = ?",
                preparation.reservationId().toString());
        assertThat(lifecycle.recoverReservations()).isEqualTo(1);
        assertThat(reservationStatus(AiUsageFeature.FOLLOW_UP, parentId())).isEqualTo("EXPIRED");
        assertThat(lifecycle.recoverReservations()).isZero();
        assertThat(totalUsage()).isEqualTo(1);
    }

    @Test
    void followUpWithoutTokenCannotSaveWhenLimitsAreEnabled() {
        realAdmissionClock();
        domainRejected("AI_REQUEST_EXPIRED", () -> answerService.saveFollowUp(
                subject(), sessionId, parentId(), null, followUpResult()));
        assertThat(count("ai_usage_reservations")).isZero();
        assertThat(totalUsage()).isZero();
    }

    @Test
    void invalidFollowUpDomainInputDoesNotCharge() {
        realAdmissionClock();
        long unanswered = tx().execute(status -> questions.saveAndFlush(InterviewQuestion.create(
                sessions.findById(sessionId).orElseThrow(), 2, InterviewQuestionType.TECHNICAL,
                QuestionGenerationSource.AI, "미답변 질문", null, NOW)).getId());
        domainRejected("INTERVIEW_ANSWER_REQUIRED", () -> answerService.prepareFollowUp(subject(), sessionId, unanswered));
        domainRejected("INTERVIEW_QUESTION_NOT_FOUND", () -> answerService.prepareFollowUp(subject(), sessionId, Long.MAX_VALUE));
        assertThat(totalUsage()).isZero();
        assertThat(count("ai_usage_reservations")).isZero();
    }

    @Test
    void followUpUsesSharedSlotsAndDailyLimit() {
        realAdmissionClock();
        invoke(Path.INITIAL, 0);
        invoke(Path.EVALUATION, 0);
        rejected("AI_PENDING_LIMIT_EXCEEDED", () -> answerService.prepareFollowUp(subject(), sessionId, parentId()));
        assertThat(totalUsage()).isEqualTo(2);
        jdbc.update("""
                UPDATE ai_usage_reservations
                SET status = 'FINISHED', finished_at = UTC_TIMESTAMP(6)
                WHERE user_id = ? AND status = 'ACTIVE'
                """, userId);
        var preparation = answerService.prepareFollowUp(subject(), sessionId, parentId());
        lifecycle.cancelFollowUp(preparation.reservationId());
        jdbc.update("UPDATE ai_usage_subject_daily SET accepted = 15 WHERE feature = 'FOLLOW_UP'");
        rejected("AI_DAILY_LIMIT_EXCEEDED", () -> answerService.prepareFollowUp(subject(), sessionId, parentId()));
        assertThat(totalUsage()).isEqualTo(3);
    }

    @Test
    void sessionCompletionDuringModelCallPreventsSaveAndCancelsSlot() {
        realAdmissionClock();
        when(followUpGenerator.generate(any())).thenAnswer(invocation -> {
            tx().executeWithoutResult(status -> sessions.findOwnedByIdForUpdate(sessionId, userId)
                    .orElseThrow().complete(NOW));
            return followUpResult();
        });
        domainRejected("INTERVIEW_SESSION_CONFLICT", () -> followUpService.generate(subject(), sessionId, parentId()));
        assertThat(reservationStatus(AiUsageFeature.FOLLOW_UP, parentId())).isEqualTo("CANCELLED");
        assertThat(questions.findByParentQuestionId(parentId())).isEmpty();
        assertThat(totalUsage()).isEqualTo(1);
    }

    @Test
    void deletionCancelsSlotsAndPreservesHmacUsageOnReSignup() {
        invoke(Path.INITIAL, 0);
        invoke(Path.EVALUATION, 0);
        deleteUser();
        assertThat(users.findById(userId)).isEmpty();
        assertThat(jdbc.queryForList("SELECT status FROM ai_usage_reservations", String.class))
                .containsExactlyInAnyOrder("CANCELLED", "CANCELLED");
        assertThat(totalUsage()).isEqualTo(2);
        assertThat(used(AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(1);
        long replacement = tx().execute(status -> users.saveAndFlush(
                User.createLocalUser("admission@example.com", "hash", "재가입")).getId());
        assertThat(identity.subjectKey(users.findById(replacement).orElseThrow().getEmail())).isEqualTo(subjectKey);
        assertThat(used(AiUsageFeature.ANSWER_EVALUATION)).isEqualTo(1);
    }

    @Test
    void deletionRollbackRestoresUserAndActiveReservation() {
        invoke(Path.EVALUATION, 0);
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            users.findByIdForUpdate(userId).orElseThrow();
            deletion.deleteLocked(userId);
            throw new IllegalStateException("delete rollback");
        })).hasMessage("delete rollback");
        assertThat(users.findById(userId)).isPresent();
        assertThat(reservationStatus(AiUsageFeature.ANSWER_EVALUATION, answerId)).isEqualTo("ACTIVE");
        assertThat(totalUsage()).isEqualTo(1);
    }

    @Test
    void recoveryCancelsOldSetNullReservationAndLateWorkerCannotWrite() {
        Running running = running(Path.EVALUATION);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        assertThat(lifecycle.recoverReservations()).isEqualTo(1);
        assertThat(reservationStatus(Path.EVALUATION.feature, answerId)).isEqualTo("CANCELLED");
        assertThat(finish(running)).isFalse();
        assertThat(globalUsed()).isEqualTo(1);
    }

    @Test
    void recoveryHonorsBatchLimitAndLeavesRealPendingWorkActive() {
        long pending = invoke(Path.INITIAL, 0);
        for (int index = 0; index < 101; index++) {
            jdbc.update("""
                    INSERT INTO ai_usage_reservations
                        (id, subject_key, user_id, feature, resource_id, status, created_at)
                    VALUES (UUID(), ?, ?, 'INITIAL_QUESTIONS', ?, 'ACTIVE', UTC_TIMESTAMP(6))
                    """, subjectKey, userId, Long.MAX_VALUE - index);
        }
        assertThat(lifecycle.recoverReservations()).isEqualTo(100);
        assertThat(lifecycle.recoverReservations()).isEqualTo(1);
        assertThat(lifecycle.recoverReservations()).isZero();
        assertThat(reservationStatus(Path.INITIAL.feature, pending)).isEqualTo("ACTIVE");
        assertThat(globalUsed()).isEqualTo(1);
    }

    @Test
    void legacyReservationWithoutBoundAttemptCanBeFinished() {
        Running running = running(Path.EVALUATION);
        jdbc.update("""
                UPDATE ai_usage_reservations
                SET worker_attempt_id = NULL, lease_expires_at = NULL
                WHERE user_id = ? AND feature = ? AND resource_id = ? AND status = 'ACTIVE'
                """, userId, running.path().feature.name(), running.resource());
        assertThat(finish(running)).isTrue();
        assertThat(reservationStatus(Path.EVALUATION.feature, answerId)).isEqualTo("FINISHED");
    }

    @ParameterizedTest
    @EnumSource(value = Path.class, names = {"INITIAL", "EVALUATION", "DRAFT"})
    void concurrentRecoveryAndCompletionDoNotLoseResultOrSlot(Path path) throws Exception {
        Running running = running(path);
        var turn = new AtomicInteger();
        var results = race(() -> turn.getAndIncrement() == 0
                ? "COMPLETE:" + finish(running)
                : "RECOVER:" + lifecycle.recoverReservations());
        assertThat(results).containsExactlyInAnyOrder("COMPLETE:true", "RECOVER:0");
        assertThat(reservationStatus(path.feature, running.resource())).isEqualTo("FINISHED");
        assertThat(globalUsed()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = Path.class, names = {"INITIAL", "EVALUATION", "DRAFT"})
    void concurrentDeletionAndCompletionLeaveNoActiveSlot(Path path) throws Exception {
        Running running = running(path);
        var turn = new AtomicInteger();
        var results = race(() -> {
            if (turn.getAndIncrement() == 0) {
                return "COMPLETE:" + finish(running);
            }
            deleteUser();
            return "DELETED";
        });
        assertThat(results).contains("DELETED");
        assertThat(users.findById(userId)).isEmpty();
        assertThat(reservationStatus(path.feature, running.resource())).isIn("FINISHED", "CANCELLED");
        assertThat(globalUsed()).isEqualTo(1);
        assertThat(finish(running)).isFalse();
    }

    @Test
    void nonMatchingReservationAttemptRollsBackFinalFailure() {
        Running running = running(Path.DRAFT);
        jdbc.update("""
                UPDATE ai_usage_reservations
                SET worker_attempt_id = UUID()
                WHERE user_id = ? AND feature = ? AND resource_id = ? AND status = 'ACTIVE'
                """, userId, running.path().feature.name(), running.resource());
        rejected("AI_USAGE_UNAVAILABLE", () -> fail(running, false));
        assertThat(jdbc.queryForObject("SELECT status FROM cover_letter_drafts WHERE id = ?",
                String.class, running.resource())).isEqualTo("RUNNING");
        assertThat(reservationStatus(Path.DRAFT.feature, running.resource())).isEqualTo("ACTIVE");
    }

    @Test
    void cleanupFailureLeavesSlotForExpiredLeaseRecovery() {
        realAdmissionClock();
        long parent = parentId();
        when(followUpGenerator.generate(any())).thenThrow(new IllegalStateException("model failed"));
        AiUsageLifecycleService target = AopTestUtils.getUltimateTargetObject(lifecycle);
        doThrow(new IllegalStateException("cleanup unavailable")).when(target).cancelFollowUp(any());
        assertThatThrownBy(() -> followUpService.generate(subject(), sessionId, parent))
                .isInstanceOfSatisfying(CatalogException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("INTERVIEW_FOLLOW_UP_UNAVAILABLE");
                    assertThat(failure.getSuppressed()).hasSize(1);
                });
        assertThat(reservationStatus(AiUsageFeature.FOLLOW_UP, parent)).isEqualTo("ACTIVE");
        jdbc.update("""
                UPDATE ai_usage_reservations
                SET lease_expires_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6))
                WHERE user_id = ? AND feature = 'FOLLOW_UP' AND resource_id = ? AND status = 'ACTIVE'
                """, userId, parent);
        assertThat(lifecycle.recoverReservations()).isEqualTo(1);
        assertThat(totalUsage()).isEqualTo(1);
    }

    @Test
    void suspensionDuringExternalCallRejectsSaveAndCancelsReservation() {
        realAdmissionClock();
        long parent = parentId();
        when(followUpGenerator.generate(any())).thenAnswer(invocation -> {
            jdbc.update("UPDATE users SET status = 'SUSPENDED', suspended_at = UTC_TIMESTAMP(6) WHERE id = ?", userId);
            return followUpResult();
        });
        domainRejected("USER_SUSPENDED", () -> followUpService.generate(subject(), sessionId, parent));
        assertThat(reservationStatus(AiUsageFeature.FOLLOW_UP, parent)).isEqualTo("CANCELLED");
        assertThat(questions.findByParentQuestionId(parent)).isEmpty();
    }

    @Test
    void deletionDuringExternalCallCancelsReservationAndRejectsLateSave() {
        realAdmissionClock();
        long parent = parentId();
        when(followUpGenerator.generate(any())).thenAnswer(invocation -> {
            deleteUser();
            return followUpResult();
        });
        assertThatThrownBy(() -> followUpService.generate(subject(), sessionId, parent))
                .isInstanceOf(com.interviewai.user.exception.UserNotFoundException.class);
        assertThat(reservationStatus(AiUsageFeature.FOLLOW_UP, parent)).isEqualTo("CANCELLED");
        assertThat(totalUsage()).isEqualTo(1);
    }

    private void deleteUser() {
        tx().executeWithoutResult(status -> {
            users.findByIdForUpdate(userId).orElseThrow();
            deletion.deleteLocked(userId);
        });
    }

    private void realAdmissionClock() {
        AiUsageRepository target = AopTestUtils.getUltimateTargetObject(usageRepository);
        doReturn(jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class)).when(target).currentUtcTime();
    }

    private long parentId() {
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT question_id FROM interview_answers WHERE id = ?", Long.class, answerId),
                "답변의 부모 질문 ID 조회 결과가 필요합니다."
        );
    }

    private InterviewFollowUpGenerator.Generated followUpResult() {
        return new InterviewFollowUpGenerator.Generated("판단 근거를 구체적으로 설명해 주세요.",
                QuestionGenerationSource.AI, "context");
    }

    private int totalUsage() {
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT COALESCE(SUM(chat_accepted), 0) FROM ai_usage_global_daily", Integer.class),
                "전체 Chat 사용량 조회 결과가 필요합니다."
        );
    }

    private String reservationStatus(AiUsageFeature feature, long resource) {
        return jdbc.queryForObject("SELECT status FROM ai_usage_reservations WHERE feature = ? AND resource_id = ? ORDER BY (status = 'ACTIVE') DESC, created_at DESC, id DESC LIMIT 1",
                String.class, feature.name(), resource);
    }

    private Running running(Path path) {
        long resource = invoke(path, 0);
        due(path, resource);
        return claim(path, resource);
    }

    private Running claim(Path path, long resource) {
        Object claim = switch (path) {
            case INITIAL -> generation.claimNext().orElseThrow();
            case EVALUATION -> evaluationExecution.claimNext().orElseThrow();
            case DRAFT -> draftExecution.claimNext().orElseThrow();
            default -> throw new IllegalArgumentException();
        };
        return new Running(path, resource, claim);
    }

    private void due(Path path, long resource) {
        jdbc.update("UPDATE " + jobTable(path) + " SET available_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) WHERE "
                + jobKey(path) + " = ?", resource);
    }

    private String jobTable(Path path) {
        return switch (path) {
            case INITIAL -> "interview_generation_jobs";
            case EVALUATION -> "interview_answer_evaluations";
            case DRAFT -> "cover_letter_drafts";
            default -> throw new IllegalArgumentException();
        };
    }

    private String jobKey(Path path) {
        return switch (path) {
            case INITIAL -> "session_id";
            case EVALUATION -> "answer_id";
            case DRAFT -> "id";
            default -> throw new IllegalArgumentException();
        };
    }

    private boolean finish(Running running) {
        return switch (running.path()) {
            case INITIAL ->
                    generation.complete((InterviewGenerationExecutionService.Claim) running.claim(), InterviewGenerationPolicy.fallback("TEST"));
            case EVALUATION -> evaluationExecution.complete((AnswerEvaluationExecutionService.Claim) running.claim(),
                    new AnswerEvaluationGenerator.Generated(new AnswerEvaluationPolicy.Result(70, 80, 90,
                            "강".repeat(20), "개".repeat(20), "개선 답변"), "context"));
            case DRAFT -> draftExecution.complete((CoverLetterDraftExecutionService.Claim) running.claim(),
                    new CoverLetterDraft.GenerateDraft("AI 제목", "AI 본문", "개선 요약", List.of()));
            default -> throw new IllegalArgumentException();
        };
    }

    private boolean fail(Running running, boolean retryable) {
        return switch (running.path()) {
            case INITIAL ->
                    generation.fail((InterviewGenerationExecutionService.Claim) running.claim(), "TEST_FAILURE", retryable);
            case EVALUATION ->
                    evaluationExecution.fail((AnswerEvaluationExecutionService.Claim) running.claim(), "TEST_FAILURE", retryable);
            case DRAFT ->
                    draftExecution.fail((CoverLetterDraftExecutionService.Claim) running.claim(), "TEST_FAILURE", retryable);
            default -> throw new IllegalArgumentException();
        };
    }

    private void assertBound(Running running) {
        String attempt = jdbc.queryForObject("SELECT attempt_id FROM " + jobTable(running.path()) + " WHERE "
                + jobKey(running.path()) + " = ?", String.class, running.resource());
        assertThat(jdbc.queryForObject("SELECT worker_attempt_id FROM ai_usage_reservations WHERE feature = ? AND resource_id = ? AND status = 'ACTIVE'",
                String.class, running.path().feature.name(), running.resource())).isEqualTo(attempt);
        LocalDateTime lease = jdbc.queryForObject("SELECT lease_expires_at FROM " + jobTable(running.path()) + " WHERE "
                + jobKey(running.path()) + " = ?", LocalDateTime.class, running.resource());
        assertThat(jdbc.queryForObject("SELECT lease_expires_at FROM ai_usage_reservations WHERE feature = ? AND resource_id = ? AND status = 'ACTIVE'",
                LocalDateTime.class, running.path().feature.name(), running.resource())).isEqualTo(lease);
    }

    private long prepare(Path path) {
        return Objects.requireNonNull(tx().execute(status -> {
            if (path == Path.INITIAL_RETRY) {
                long id = sessions.saveAndFlush(newSession(users.findById(userId).orElseThrow())).getId();
                generation.register(id);
                failGeneration(id);
                return id;
            }
            if (path == Path.EVALUATION_RETRY) {
                var evaluation = evaluations.saveAndFlush(InterviewAnswerEvaluation.create(
                        answers.findById(answerId).orElseThrow(), InterviewGenerationPolicy.Mode.AI,
                        "test-model", "test-version", NOW));
                jdbc.update("UPDATE interview_answer_evaluations SET status = 'FAILED', failure_code = 'TEST_FAILURE' WHERE id = ?",
                        evaluation.getId());
                return answerId;
            }
            if (path == Path.DRAFT_REGENERATE) {
                var draft = CoverLetterDraft.create(users.findById(userId).orElseThrow(),
                        coverLetters.findById(coverLetterId).orElseThrow(), null, input(), "a".repeat(64),
                        CoverLetterDraftPolicy.PROMPT_TEMPLATE_VERSION, "test-model", NOW);
                draft.failPending("TEST_FAILURE", NOW);
                return drafts.saveAndFlush(draft).getId();
            }
            return 0L;
        }));
    }

    private long invoke(Path path, long sourceId) {
        return switch (path) {
            case INITIAL -> sessionService.create(subject(), new CreateInterviewSessionRequest(20L)).id();
            case INITIAL_RETRY -> {
                generation.retry(userId, sourceId);
                yield sourceId;
            }
            case EVALUATION -> {
                evaluationService.request(subject(), sessionId, answerId);
                yield answerId;
            }
            case EVALUATION_RETRY -> {
                evaluationService.retry(subject(), sessionId, answerId);
                yield answerId;
            }
            case DRAFT -> draftService.create(subject(), coverLetterId, DRAFT_REQUEST).id();
            case DRAFT_REGENERATE -> draftService.regenerate(subject(), coverLetterId, sourceId).id();
        };
    }

    private void failGeneration(long id) {
        jdbc.update("UPDATE interview_sessions SET status = 'FAILED', failure_code = 'TEST_FAILURE' WHERE id = ?", id);
        jdbc.update("UPDATE interview_generation_jobs SET status = 'FAILED', last_error_code = 'TEST_FAILURE' WHERE session_id = ?", id);
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : List.of("interview_sessions", "interview_generation_jobs", "interview_answer_evaluations",
                "cover_letter_drafts", "ai_usage_subjects", "ai_usage_subject_daily", "ai_usage_global_daily", "ai_usage_reservations")) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table));
        }
        return result;
    }

    private int count(String table) {
        if (!List.of("interview_sessions", "interview_generation_jobs", "interview_answer_evaluations",
                "ai_usage_reservations").contains(table)) throw new IllegalArgumentException(table);
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
    }

    private int used(AiUsageFeature feature) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COALESCE(SUM(accepted), 0) FROM ai_usage_subject_daily WHERE subject_key = ? AND usage_date = ? AND feature = ?",
                Integer.class, subjectKey, DAY, feature.name()));
    }

    private int globalUsed() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COALESCE(SUM(chat_accepted), 0) FROM ai_usage_global_daily WHERE usage_date = ?",
                Integer.class, DAY));
    }

    private String subject() {
        return Long.toString(userId);
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    private InterviewSession newSession(User user) {
        return InterviewSession.create(user, 20L, null, null, 40L, "회사", "공고", "Backend", "본문",
                null, null, null, null, NOW);
    }

    private CoverLetterDraft.InputSnapshot input() {
        return new CoverLetterDraft.InputSnapshot(20L, 30L, 1, "기존 제목", "기존 본문", 40L,
                "회사", "IT", "회사 설명", null, null, "공고", "Backend", "FULL_TIME", null,
                "공고 본문", null, null, null, "이력서", "이력서 본문", "강조");
    }

    private void rejected(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AiUsageException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private void domainRejected(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private <T> List<T> race(Callable<T> action) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                Callable<T> task = () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return action.call();
                };
                var first = executor.submit(task);
                var second = executor.submit(task);
                return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    enum Path {
        INITIAL(AiUsageFeature.INITIAL_QUESTIONS), INITIAL_RETRY(AiUsageFeature.INITIAL_QUESTIONS),
        EVALUATION(AiUsageFeature.ANSWER_EVALUATION), EVALUATION_RETRY(AiUsageFeature.ANSWER_EVALUATION),
        DRAFT(AiUsageFeature.COVER_LETTER_DRAFT), DRAFT_REGENERATE(AiUsageFeature.COVER_LETTER_DRAFT);
        final AiUsageFeature feature;

        Path(AiUsageFeature feature) {
            this.feature = feature;
        }
    }

    private record Running(Path path, long resource, Object claim) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        Clock catalogClock() {
            return Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }

        @Bean
        AiUsageProperties aiUsageProperties() {
            return new AiUsageProperties(true, true, true, AiUsageConfigurationTest.TEST_KEY,
                    3, 15, 30, 3, 20, 2, 50, 500, 10, 90);
        }

        @Bean
        InterviewGenerationProperties generationProperties() {
            return new InterviewGenerationProperties(false, InterviewGenerationPolicy.Mode.AI, "test-model");
        }

        @Bean
        AnswerEvaluationProperties evaluationProperties() {
            return new AnswerEvaluationProperties(true, InterviewGenerationPolicy.Mode.AI, "test-model",
                    Duration.ofSeconds(1), Duration.ZERO, 20);
        }

        @Bean
        CoverLetterDraftProperties draftProperties() {
            return new CoverLetterDraftProperties(true, "test-model", Duration.ofSeconds(1), Duration.ZERO, 20);
        }
    }
}
