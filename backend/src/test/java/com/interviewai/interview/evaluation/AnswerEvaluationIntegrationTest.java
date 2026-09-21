package com.interviewai.interview.evaluation;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.interview.entity.InterviewAnswer;
import com.interviewai.interview.entity.InterviewQuestion;
import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.enums.AnswerEvaluationStatus;
import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.QuestionGenerationSource;
import com.interviewai.interview.generation.InterviewGenerationPolicy;
import com.interviewai.interview.repository.InterviewAnswerRepository;
import com.interviewai.interview.repository.InterviewQuestionRepository;
import com.interviewai.interview.repository.InterviewSessionRepository;
import com.interviewai.interview.service.AnswerEvaluationService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({AnswerEvaluationService.class, AnswerEvaluationExecutionService.class,
        AdminAuthorizationService.class, AnswerEvaluationIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AnswerEvaluationIntegrationTest extends MySqlIntegrationTest {
    @Autowired AnswerEvaluationService service;
    @Autowired AnswerEvaluationExecutionService execution;
    @Autowired UserRepository users;
    @Autowired InterviewSessionRepository sessions;
    @Autowired InterviewQuestionRepository questions;
    @Autowired InterviewAnswerRepository answers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (long id : userIds) jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    @Test
    void migratesRequestsClaimsAndPersistsResult() {
        var f = fixture();
        var requested = service.request(f.subject(), f.session(), f.answer());
        due(requested.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '14' AND success = TRUE", Integer.class)).isEqualTo(1);
        assertThat(service.request(f.subject(), f.session(), f.answer()).id()).isEqualTo(requested.id());
        var claim = execution.claimNext().orElseThrow();
        assertThat(claim.input().answer()).isEqualTo("저장된 답변");
        assertThat(claim.model()).isEqualTo("test-model");
        assertThat(execution.claimNext()).isEmpty();
        assertThat(execution.complete(claim, generated())).isTrue();
        assertThat(execution.complete(claim, generated())).isFalse();
        var result = service.get(f.subject(), f.session(), f.answer());
        assertThat(result.status()).isEqualTo(AnswerEvaluationStatus.COMPLETED);
        assertThat(result.improvements()).isEqualTo("개".repeat(20));
        assertThat(result.completedAt()).isNotNull();
    }

    @Test
    void hidesOwnersAndAnswersFromOtherSessions() {
        var f = fixture();
        var other = fixture();
        code(() -> service.request(other.subject(), f.session(), f.answer()), "INTERVIEW_SESSION_NOT_FOUND");
        code(() -> service.get(other.subject(), f.session(), f.answer()), "INTERVIEW_SESSION_NOT_FOUND");
        code(() -> service.retry(other.subject(), f.session(), f.answer()), "INTERVIEW_SESSION_NOT_FOUND");
        code(() -> service.request(f.subject(), f.session(), other.answer()), "INTERVIEW_ANSWER_NOT_FOUND");
        code(() -> service.get(f.subject(), f.session(), f.answer()), "ANSWER_EVALUATION_NOT_FOUND");
    }

    @Test
    void recoversLeaseRejectsLateWritesAndPersistsManualRetry() {
        var f = fixture();
        var id = service.request(f.subject(), f.session(), f.answer()).id();
        due(id);
        var old = execution.claimNext().orElseThrow();
        jdbc.update("UPDATE interview_answer_evaluations SET lease_expires_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) WHERE id = ?", id);
        assertThat(execution.complete(old, generated())).isFalse();
        assertThat(execution.recoverOne()).isTrue();
        due(id);
        var current = execution.claimNext().orElseThrow();
        assertThat(current.attemptId()).isNotEqualTo(old.attemptId());
        assertThat(execution.fail(old, "OLD_FAILURE", false)).isFalse();
        assertThat(execution.fail(current, "FINAL_FAILURE", false)).isTrue();
        assertThat(service.retry(f.subject(), f.session(), f.answer()).manualRetryCount()).isEqualTo(1);
        assertThat(service.get(f.subject(), f.session(), f.answer()).status()).isEqualTo(AnswerEvaluationStatus.PENDING);
        code(() -> service.retry(f.subject(), f.session(), f.answer()), "ANSWER_EVALUATION_RETRY_CONFLICT");
    }

    @Test
    void rollbackPreservesProcessingAndUserDeletionCascades() {
        var f = fixture();
        var id = service.request(f.subject(), f.session(), f.answer()).id();
        due(id);
        var claim = execution.claimNext().orElseThrow();
        var invalid = new AnswerEvaluationGenerator.Generated(generated().result(), " ");
        assertThatThrownBy(() -> execution.complete(claim, invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.get(f.subject(), f.session(), f.answer()).status()).isEqualTo(AnswerEvaluationStatus.PROCESSING);
        assertThat(service.get(f.subject(), f.session(), f.answer()).starScore()).isNull();
        jdbc.update("DELETE FROM users WHERE id = ?", Long.parseLong(f.subject()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_answer_evaluations WHERE id = ?", Integer.class, id)).isZero();
        assertThat(execution.complete(claim, generated())).isFalse();
    }

    @Test
    void concurrentRequestsHaveOneRowAndConcurrentClaimsHaveOneWinner() throws Exception {
        var f = fixture();
        var ids = race(() -> service.request(f.subject(), f.session(), f.answer()).id());
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        due(ids.getFirst());
        var claims = race(() -> execution.claimNext().isPresent());
        assertThat(claims).containsExactlyInAnyOrder(true, false);
    }

    private <T> List<T> race(Callable<T> action) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<T> task = () -> { barrier.await(5, TimeUnit.SECONDS); return action.call(); };
            var a = executor.submit(task);
            var b = executor.submit(task);
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    private void due(long id) {
        jdbc.update("UPDATE interview_answer_evaluations SET available_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) WHERE id = ?", id);
    }

    private Fixture fixture() {
        return new TransactionTemplate(manager).execute(status -> {
            var now = LocalDateTime.of(2026, 9, 15, 0, 0);
            var user = users.saveAndFlush(User.createLocalUser(UUID.randomUUID() + "@example.com", "hash", "사용자"));
            userIds.add(user.getId());
            var session = InterviewSession.create(user, 1L, null, null, 2L, "회사", "공고", "Backend", "본문", null, null, null, null, now);
            session.markReady(now);
            session.start(now);
            sessions.saveAndFlush(session);
            var question = questions.saveAndFlush(InterviewQuestion.create(session, 1, InterviewQuestionType.TECHNICAL,
                    QuestionGenerationSource.AI, "질문", null, now));
            var answer = answers.saveAndFlush(InterviewAnswer.create(question, "저장된 답변", now));
            return new Fixture(user.getId().toString(), session.getId(), answer.getId());
        });
    }

    private AnswerEvaluationGenerator.Generated generated() {
        return new AnswerEvaluationGenerator.Generated(new AnswerEvaluationPolicy.Result(0, 50, 100,
                "강".repeat(20), "개".repeat(20), "개선 답변"), "private context");
    }

    private void code(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private record Fixture(String subject, long session, long answer) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean Clock catalogClock() { return Clock.systemUTC(); }
        @Bean AnswerEvaluationProperties evaluationProperties() {
            return new AnswerEvaluationProperties(true, InterviewGenerationPolicy.Mode.AI, "test-model",
                    Duration.ofSeconds(1), Duration.ZERO, 20);
        }
    }
}
