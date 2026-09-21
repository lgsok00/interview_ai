package com.interviewai.coverletter.draft;

import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
@Import(CoverLetterDraftExecutionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CoverLetterDraftIntegrationTest extends MySqlIntegrationTest {

    private final List<Long> userIds = new ArrayList<>();
    @Autowired
    CoverLetterDraftExecutionService execution;
    @Autowired
    CoverLetterDraftRepository drafts;
    @Autowired
    CoverLetterRepository coverLetters;
    @Autowired
    UserRepository users;
    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        for (long userId : userIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void migratesClaimsAndPersistsGeneratedResult() {
        long id = draft();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '20' AND success = TRUE",
                Integer.class
        )).isEqualTo(1);

        CoverLetterDraftExecutionService.Claim claim = execution.claimNext().orElseThrow();
        assertThat(claim.draftId()).isEqualTo(id);
        assertThat(claim.model()).isEqualTo("test-model");
        assertThat(claim.input().coverLetterContent()).isEqualTo("기존 본문");
        assertThat(execution.claimNext()).isEmpty();

        assertThat(execution.complete(claim, generated())).isTrue();
        assertThat(execution.complete(claim, generated())).isFalse();

        CoverLetterDraft saved = drafts.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(CoverLetterDraftStatus.REVIEW_READY);
        assertThat(saved.getGeneratedTitle()).isEqualTo("AI 제목");
        assertThat(saved.getWarnings()).containsExactly("사실 확인 필요");
    }

    @Test
    void expiredLeaseIsReclaimedAndLateAttemptCannotWrite() {
        long id = draft();
        CoverLetterDraftExecutionService.Claim old = execution.claimNext().orElseThrow();
        expire(id);

        CoverLetterDraftExecutionService.Claim current = execution.claimNext().orElseThrow();
        assertThat(current.attemptId()).isNotEqualTo(old.attemptId());
        assertThat(execution.complete(old, generated())).isFalse();
        assertThat(execution.fail(old, "OLD_FAILURE", false)).isFalse();
        assertThat(execution.complete(current, generated())).isTrue();
        assertThat(drafts.findById(id).orElseThrow().getAttemptCount()).isEqualTo(2);
    }

    @Test
    void exhaustedExpiredLeaseIsFailedInsteadOfReclaimed() {
        long id = draft();
        jdbc.update("""
                UPDATE cover_letter_drafts
                SET status = 'RUNNING', attempt_count = 3,
                    attempt_id = ?, lease_expires_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6))
                WHERE id = ?
                """, UUID.randomUUID().toString(), id);

        assertThat(execution.claimNext()).isEmpty();
        assertThat(execution.recoverOneExhaustedLease()).isTrue();

        CoverLetterDraft saved = drafts.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(CoverLetterDraftStatus.FAILED);
        assertThat(saved.getFailureCode()).isEqualTo("DRAFT_LEASE_EXPIRED");
    }

    @Test
    void concurrentClaimsHaveExactlyOneWinner() throws Exception {
        draft();

        List<Boolean> claims = race(() -> execution.claimNext().isPresent());

        assertThat(claims).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void invalidCompletionRollsBackAndKeepsActiveAttempt() {
        long id = draft();
        CoverLetterDraftExecutionService.Claim claim = execution.claimNext().orElseThrow();

        assertThatThrownBy(() -> execution.complete(
                claim,
                new CoverLetterDraft.GenerateDraft(" ", "본문", "요약", List.of())
        )).isInstanceOf(IllegalArgumentException.class);

        CoverLetterDraft saved = drafts.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(CoverLetterDraftStatus.RUNNING);
        assertThat(saved.getAttemptId()).isEqualTo(claim.attemptId());
        assertThat(saved.getGeneratedContent()).isNull();
    }

    private long draft() {
        User user = users.saveAndFlush(User.createLocalUser(
                UUID.randomUUID() + "@example.com", "encoded", "사용자"
        ));
        userIds.add(user.getId());
        CoverLetter coverLetter = coverLetters.saveAndFlush(CoverLetter.create(user, "기존 제목"));
        LocalDateTime now = jdbc.queryForObject(
                "SELECT TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6))",
                LocalDateTime.class
        );

        CoverLetterDraft draft = CoverLetterDraft.create(
                user,
                coverLetter,
                null,
                new CoverLetterDraft.InputSnapshot(
                        20L, null, 1, "기존 제목", "기존 본문",
                        40L, "회사", "IT", "회사 설명", null, "서울",
                        "백엔드 개발자", "Backend", "FULL_TIME", "서울", "공고 설명", null,
                        null, null, null, null, "직무 적합성을 강조해 주세요."
                ),
                "a".repeat(64),
                CoverLetterDraftPolicy.PROMPT_TEMPLATE_VERSION,
                "test-model",
                now
        );

        return drafts.saveAndFlush(draft).getId();
    }

    private void expire(long id) {
        jdbc.update("""
                UPDATE cover_letter_drafts
                SET lease_expires_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6))
                WHERE id = ?
                """, id);
    }

    private CoverLetterDraft.GenerateDraft generated() {
        return new CoverLetterDraft.GenerateDraft(
                "AI 제목", "AI 본문", "직무 연관성을 강화했습니다.", List.of("사실 확인 필요")
        );
    }

    private <T> List<T> race(Callable<T> action) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<T> task = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return action.call();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }
}
