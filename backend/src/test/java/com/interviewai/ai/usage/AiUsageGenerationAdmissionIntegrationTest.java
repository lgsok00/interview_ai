package com.interviewai.ai.usage;

import com.interviewai.coverletter.draft.CoverLetterDraft;
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
import com.interviewai.interview.evaluation.AnswerEvaluationProperties;
import com.interviewai.interview.generation.InterviewGenerationExecutionService;
import com.interviewai.interview.generation.InterviewGenerationPolicy;
import com.interviewai.interview.generation.InterviewGenerationProperties;
import com.interviewai.interview.repository.InterviewAnswerEvaluationRepository;
import com.interviewai.interview.repository.InterviewAnswerRepository;
import com.interviewai.interview.repository.InterviewQuestionRepository;
import com.interviewai.interview.repository.InterviewSessionRepository;
import com.interviewai.interview.service.AnswerEvaluationService;
import com.interviewai.interview.service.InterviewSessionService;
import com.interviewai.interview.service.InterviewSessionSnapshotAssembler;
import com.interviewai.interview.service.InterviewSourceSnapshot;
import com.interviewai.rag.service.RagSourceChangeRegistrationService;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * 실제 도메인 서비스와 사용량 저장소를 연결한다. 원본 조립만 격리하며 외부 AI는 호출하지 않는다.
 */
@DataJpaTest
@Import({AiUsageAdmissionService.class, AiUsageRepository.class, AiUsageIdentity.class,
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
