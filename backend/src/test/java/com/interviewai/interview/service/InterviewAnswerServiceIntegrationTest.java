package com.interviewai.interview.service;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.interview.dto.InterviewAnswerResponse;
import com.interviewai.interview.dto.SubmitInterviewAnswerRequest;
import com.interviewai.interview.entity.InterviewQuestion;
import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.QuestionGenerationSource;
import com.interviewai.interview.generation.InterviewFollowUpGenerator;
import com.interviewai.interview.repository.InterviewQuestionRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({InterviewAnswerService.class, AdminAuthorizationService.class, InterviewAnswerServiceIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InterviewAnswerServiceIntegrationTest extends MySqlIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 0, 0);
    private static final InterviewFollowUpGenerator.Generated GENERATED = new InterviewFollowUpGenerator.Generated(
            "선택한 접근 방법의 판단 근거를 구체적으로 설명해 주세요.", QuestionGenerationSource.AI, "private context");
    private final List<Long> userIds = new ArrayList<>();
    @Autowired
    InterviewAnswerService service;
    @Autowired
    InterviewSessionRepository sessions;
    @Autowired
    InterviewQuestionRepository questions;
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
    void storesAnswerSnapshotFollowUpAndOrderedAnswers() {
        var f = fixture();
        var answer = service.submit(f.subject(), f.session(), f.first(), request("  첫 답변  "));
        assertThat(answer.content()).isEqualTo("첫 답변");
        assertThat(answer.createdAt().toInstant()).isEqualTo(Instant.parse("2026-09-15T00:00:00Z"));
        var prepared = service.prepareFollowUp(f.subject(), f.session(), f.first());
        assertThat(prepared.input().answer()).isEqualTo("첫 답변");
        assertThat(prepared.input().jobRole()).isEqualTo("Backend");
        var followUp = service.saveFollowUp(f.subject(), f.session(), f.first(), GENERATED);
        assertThat(followUp.parentQuestionId()).isEqualTo(f.first());
        assertThat(followUp.question().sequenceNumber()).isEqualTo(6);
        assertThat(followUp.question().questionType()).isEqualTo(InterviewQuestionType.FOLLOW_UP);
        service.submit(f.subject(), f.session(), followUp.question().id(), request("추가 답변"));
        service.submit(f.subject(), f.session(), f.second(), request("두 번째 답변"));
        var result = service.getAll(f.subject(), f.session());
        assertThat(result).extracting(InterviewAnswerResponse::questionId).containsExactly(f.first(), f.second(), followUp.question().id());
        assertThat(result.getFirst().followUpQuestionId()).isEqualTo(followUp.question().id());
        assertThat(result.getLast().followUpQuestionId()).isNull();
        assertThat(jdbc.queryForObject("SELECT context_snapshot FROM interview_questions WHERE id = ?",
                String.class, followUp.question().id())).isEqualTo("private context");
        code(() -> service.prepareFollowUp(f.subject(), f.session(), followUp.question().id()),
                "INTERVIEW_FOLLOW_UP_DEPTH_EXCEEDED");
    }

    @Test
    void completedSessionAllowsReplayButRejectsNewWrites() {
        var f = fixture();
        var answer = service.submit(f.subject(), f.session(), f.first(), request("답변"));
        var followUp = service.saveFollowUp(f.subject(), f.session(), f.first(), GENERATED);
        complete(f);
        assertThat(service.submit(f.subject(), f.session(), f.first(), request(" 답변 ")).id()).isEqualTo(answer.id());
        assertThat(service.prepareFollowUp(f.subject(), f.session(), f.first()).existing()).isEqualTo(followUp);
        assertThat(service.saveFollowUp(f.subject(), f.session(), f.first(), GENERATED)).isEqualTo(followUp);
        code(() -> service.submit(f.subject(), f.session(), f.first(), request("변경")), "INTERVIEW_ANSWER_CONFLICT");
        code(() -> service.submit(f.subject(), f.session(), f.second(), request("답변")), "INTERVIEW_SESSION_CONFLICT");
        code(() -> service.prepareFollowUp(f.subject(), f.session(), f.second()), "INTERVIEW_SESSION_CONFLICT");
    }

    @Test
    void rejectsMissingAnswerAndLateGenerationAfterCompletion() {
        var f = fixture();
        code(() -> service.prepareFollowUp(f.subject(), f.session(), f.first()), "INTERVIEW_ANSWER_REQUIRED");
        code(() -> service.saveFollowUp(f.subject(), f.session(), f.first(), GENERATED), "INTERVIEW_ANSWER_REQUIRED");
        service.submit(f.subject(), f.session(), f.first(), request("답변"));
        service.prepareFollowUp(f.subject(), f.session(), f.first());
        complete(f);
        code(() -> service.saveFollowUp(f.subject(), f.session(), f.first(), GENERATED), "INTERVIEW_SESSION_CONFLICT");
        assertThat(questions.findByParentQuestionId(f.first())).isEmpty();
        assertThat(service.getAll(f.subject(), f.session())).hasSize(1);
    }

    @Test
    void hidesOtherOwnersAndQuestionsFromOtherSessions() {
        var f = fixture();
        var other = fixture();
        code(() -> service.getAll(other.subject(), f.session()), "INTERVIEW_SESSION_NOT_FOUND");
        code(() -> service.submit(other.subject(), f.session(), f.first(), request("답변")), "INTERVIEW_SESSION_NOT_FOUND");
        code(() -> service.prepareFollowUp(other.subject(), f.session(), f.first()), "INTERVIEW_SESSION_NOT_FOUND");
        code(() -> service.saveFollowUp(other.subject(), f.session(), f.first(), GENERATED), "INTERVIEW_SESSION_NOT_FOUND");
        code(() -> service.submit(f.subject(), f.session(), other.first(), request("답변")), "INTERVIEW_QUESTION_NOT_FOUND");
        assertThat(service.getAll(f.subject(), f.session())).isEmpty();
    }

    @Test
    void validatesDirectServiceCallsAndNonRunningStates() {
        var f = fixture();
        for (String text : new String[]{null, " \n", "가".repeat(10001)}) {
            code(() -> service.submit(f.subject(), f.session(), f.first(), request(text)), "VALIDATION_ERROR");
        }
        for (String state : List.of("GENERATING", "READY", "FAILED")) {
            jdbc.update("UPDATE interview_sessions SET status = ?, failure_code = ? WHERE id = ?",
                    state, state.equals("FAILED") ? "TEST" : null, f.session());
            code(() -> service.submit(f.subject(), f.session(), f.first(), request("답변")), "INTERVIEW_SESSION_CONFLICT");
        }
    }

    @Test
    void appliesV13AndDatabaseConstraints() {
        var f = fixture();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '13' AND success = TRUE",
                Integer.class)).isEqualTo(1);
        service.submit(f.subject(), f.session(), f.first(), request("가".repeat(10000)));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO interview_answers(question_id, content, created_at) VALUES (?, ?, ?)",
                f.first(), "중복", NOW)).isInstanceOf(DataAccessException.class);
        for (String invalid : List.of("", "가".repeat(10001))) {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO interview_answers(question_id, content, created_at) VALUES (?, ?, ?)",
                    f.second(), invalid, NOW)).isInstanceOf(DataAccessException.class);
        }
        service.saveFollowUp(f.subject(), f.session(), f.first(), GENERATED);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO interview_questions(session_id, sequence_number, question_type, generation_source,
                    content, created_at, parent_question_id) VALUES (?, 7, 'FOLLOW_UP', 'AI', '중복', ?, ?)
                """, f.session(), NOW, f.first())).isInstanceOf(DataAccessException.class);
    }

    @Test
    void rollsBackFailedFollowUpWithoutLosingAnswer() {
        var f = fixture();
        service.submit(f.subject(), f.session(), f.first(), request("보존할 답변"));
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            service.saveFollowUp(f.subject(), f.session(), f.first(), GENERATED);
            jdbc.update("INSERT INTO interview_answers(question_id, content, created_at) VALUES (?, '중복', ?)", f.first(), NOW);
        })).isInstanceOf(DataAccessException.class);
        assertThat(questions.findByParentQuestionId(f.first())).isEmpty();
        assertThat(service.getAll(f.subject(), f.session())).singleElement().satisfies(r ->
                assertThat(r.content()).isEqualTo("보존할 답변"));
    }

    @Test
    void cascadesAnswersAndFollowUpsOnSessionAndUserDeletion() {
        for (boolean deleteUser : List.of(false, true)) {
            var f = fixture();
            var answer = service.submit(f.subject(), f.session(), f.first(), request("답변"));
            var followUp = service.saveFollowUp(f.subject(), f.session(), f.first(), GENERATED);
            var childAnswer = service.submit(f.subject(), f.session(), followUp.question().id(), request("추가 답변"));
            if (deleteUser) jdbc.update("DELETE FROM users WHERE id = ?", Long.parseLong(f.subject()));
            else jdbc.update("DELETE FROM interview_sessions WHERE id = ?", f.session());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_answers WHERE id IN (?, ?)",
                    Integer.class, answer.id(), childAnswer.id())).isZero();
            assertThat(questions.findById(followUp.question().id())).isEmpty();
        }
    }

    @Test
    void concurrentIdenticalSubmissionsReturnSameAnswer() throws Exception {
        var f = fixture();
        var result = race(
                () -> service.submit(f.subject(), f.session(), f.first(), request("답변")).id(),
                () -> service.submit(f.subject(), f.session(), f.first(), request("답변")).id());
        assertThat(result.get(0)).isEqualTo(result.get(1));
        assertThat(service.getAll(f.subject(), f.session())).hasSize(1);
    }

    @Test
    void concurrentSameParentGenerationReturnsSameQuestion() throws Exception {
        var f = fixture();
        service.submit(f.subject(), f.session(), f.first(), request("답변"));
        var result = race(
                () -> service.saveFollowUp(f.subject(), f.session(), f.first(), GENERATED).question().id(),
                () -> service.saveFollowUp(f.subject(), f.session(), f.first(), GENERATED).question().id());
        assertThat(result.get(0)).isEqualTo(result.get(1));
    }

    @Test
    void concurrentDifferentParentsAllocateDistinctSequences() throws Exception {
        var f = fixture();
        service.submit(f.subject(), f.session(), f.first(), request("답변1"));
        service.submit(f.subject(), f.session(), f.second(), request("답변2"));
        var result = race(
                () -> service.saveFollowUp(f.subject(), f.session(), f.first(), GENERATED).question().sequenceNumber(),
                () -> service.saveFollowUp(f.subject(), f.session(), f.second(), GENERATED).question().sequenceNumber());
        assertThat(result).containsExactlyInAnyOrder(6, 7);
    }

    // Both transactions establish an InnoDB read view before competing for the session lock.
    // This deterministically covers the authorization SELECT occurring before the locking SELECT.
    private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> concurrentTransaction(barrier, first));
            var b = executor.submit(() -> concurrentTransaction(barrier, second));
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    private <T> T concurrentTransaction(CyclicBarrier barrier, Callable<T> action) {
        return tx().execute(status -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM interview_answers", Integer.class);
            try {
                barrier.await(5, TimeUnit.SECONDS);
                return action.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private Fixture fixture() {
        return tx().execute(status -> {
            var user = users.saveAndFlush(User.createLocalUser(UUID.randomUUID() + "@example.com", "hash", "사용자"));
            userIds.add(user.getId());
            var session = InterviewSession.create(user, 1L, null, null, 2L, "회사", "공고", "Backend", "본문",
                    null, null, null, null, NOW);
            session.markReady(NOW);
            session.start(NOW);
            sessions.saveAndFlush(session);
            var ids = new ArrayList<Long>();
            for (int i = 1; i <= 5; i++) {
                ids.add(questions.saveAndFlush(InterviewQuestion.create(session, i, InterviewQuestionType.TECHNICAL,
                        QuestionGenerationSource.AI, "초기 질문 " + i, null, NOW)).getId());
            }
            return new Fixture(user.getId().toString(), session.getId(), ids.get(0), ids.get(1));
        });
    }

    private void complete(Fixture f) {
        tx().executeWithoutResult(status -> sessions.findOwnedByIdForUpdate(f.session(), Long.parseLong(f.subject()))
                .orElseThrow().complete(NOW.plusMinutes(1)));
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private SubmitInterviewAnswerRequest request(String text) {
        return new SubmitInterviewAnswerRequest(text);
    }

    private void code(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(code));
    }

    private record Fixture(String subject, long session, long first, long second) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        Clock catalogClock() {
            return Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
