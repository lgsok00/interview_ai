package com.interviewai.coverletter.draft;

import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CoverLetterDraftTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 10, 0);
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void createsPendingDraftWithImmutableInputMetadata() {
        CoverLetterDraft draft = draft();

        assertThat(draft.getStatus()).isEqualTo(CoverLetterDraftStatus.PENDING);
        assertThat(draft.getAttemptCount()).isZero();
        assertThat(draft.getBaseVersionNumber()).isEqualTo(1);
        assertThat(draft.getInputHash()).hasSize(64);
        assertThat(draft.getGeneratedContent()).isNull();
        assertThat(draft.getFailureCode()).isNull();
    }

    @Test
    void immediatelyFailsUnconfiguredPendingDraftWithoutCreatingFallback() {
        CoverLetterDraft draft = draft();

        draft.failPending("DRAFT_AI_NOT_CONFIGURED", NOW.plusSeconds(1));

        assertThat(draft.getStatus()).isEqualTo(CoverLetterDraftStatus.FAILED);
        assertThat(draft.getFailureCode()).isEqualTo("DRAFT_AI_NOT_CONFIGURED");
        assertThat(draft.getGeneratedTitle()).isNull();
        assertThat(draft.getGeneratedContent()).isNull();
        assertThatIllegalStateException()
                .isThrownBy(() -> draft.failPending("DRAFT_AI_NOT_CONFIGURED", NOW.plusSeconds(2)));
    }

    @Test
    void rejectsLateAttemptAfterRetryAndStoresOnlyCurrentResult() {
        CoverLetterDraft draft = draft();
        draft.claim(FIRST, NOW);
        draft.retry(FIRST.toString(), NOW.plusSeconds(126), NOW.plusSeconds(1));
        draft.claim(SECOND, NOW.plusSeconds(126));

        assertThatIllegalStateException().isThrownBy(() -> draft.complete(
                FIRST.toString(), generated(), NOW.plusSeconds(127)));

        draft.complete(SECOND.toString(), generated(), NOW.plusSeconds(127));

        assertThat(draft.getStatus()).isEqualTo(CoverLetterDraftStatus.REVIEW_READY);
        assertThat(draft.getAttemptCount()).isEqualTo(2);
        assertThat(draft.getAttemptId()).isNull();
        assertThat(draft.getLeaseExpiresAt()).isNull();
        assertThat(draft.getGeneratedTitle()).isEqualTo("AI 제목");
        assertThat(draft.getWarnings()).containsExactly("사실 확인 필요");
    }

    @Test
    void rejectsCompletionAtLeaseBoundary() {
        CoverLetterDraft draft = draft();
        draft.claim(FIRST, NOW);

        assertThatIllegalStateException().isThrownBy(() -> draft.complete(
                FIRST.toString(), generated(), NOW.plusSeconds(CoverLetterDraft.LEASE_SECONDS)));
    }

    @Test
    void appliesReviewReadyDraftOnceAndRecognizesSameNormalizedContent() {
        CoverLetterDraft draft = draft();
        draft.claim(FIRST, NOW);
        draft.complete(FIRST.toString(), generated(), NOW.plusSeconds(1));

        draft.apply(2, " 적용 제목 ", " 적용 본문 ", NOW.plusSeconds(2));

        assertThat(draft.getStatus()).isEqualTo(CoverLetterDraftStatus.APPLIED);
        assertThat(draft.getAppliedVersionNumber()).isEqualTo(2);
        assertThat(draft.hasSameAppliedContent("적용 제목", "적용 본문")).isTrue();
        assertThat(draft.hasSameAppliedContent("다른 제목", "적용 본문")).isFalse();
        assertThatIllegalStateException()
                .isThrownBy(() -> draft.apply(3, "다시", "다시", NOW.plusSeconds(3)));
    }

    @Test
    void validatesGeneratedOutputBeforeChangingState() {
        CoverLetterDraft draft = draft();
        draft.claim(FIRST, NOW);

        var invalid = new CoverLetterDraft.GenerateDraft(" ", "본문", "요약", List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> draft.complete(FIRST.toString(), invalid, NOW.plusSeconds(1)));
        assertThat(draft.getStatus()).isEqualTo(CoverLetterDraftStatus.RUNNING);
        assertThat(draft.getGeneratedTitle()).isNull();
        assertThat(draft.getAttemptId()).isEqualTo(FIRST.toString());
    }

    private CoverLetterDraft draft() {
        User user = User.createLocalUser("user@example.com", "encoded", "사용자");
        ReflectionTestUtils.setField(user, "id", 1L);
        CoverLetter coverLetter = CoverLetter.create(user, "기존 제목");
        ReflectionTestUtils.setField(coverLetter, "id", 10L);

        return CoverLetterDraft.create(
                user,
                coverLetter,
                null,
                new CoverLetterDraft.InputSnapshot(
                        20L, 30L, 1, "기존 제목", "기존 본문",
                        40L, "회사", "IT", "회사 설명", "https://example.com", "서울",
                        "백엔드 개발자", "백엔드", "FULL_TIME", "서울", "공고 설명",
                        "https://example.com/jobs/1", NOW, NOW.plusDays(30),
                        "이력서", "이력서 본문", "직무 적합성을 강조해 주세요."
                ),
                "a".repeat(64),
                CoverLetterDraftPolicy.PROMPT_TEMPLATE_VERSION,
                "test-model",
                NOW
        );
    }

    private CoverLetterDraft.GenerateDraft generated() {
        return new CoverLetterDraft.GenerateDraft(
                "AI 제목",
                "AI 본문",
                "직무 연관성을 강화했습니다.",
                List.of("사실 확인 필요")
        );
    }
}
