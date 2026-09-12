package com.interviewai.interview.entity;

import com.interviewai.interview.enums.InterviewSessionStatus;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class InterviewSessionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 10, 0);
    private static final LocalDateTime LATER = NOW.plusMinutes(1);


    @Test
    @DisplayName("선택 문서 스냅샷을 포함한 생성 중 세션을 만든다")
    void createsGeneratingSessionWithSnapshots() {
        InterviewSession session = createSession(2L, 3L);

        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.GENERATING);
        assertThat(session.getJobPostingId()).isEqualTo(1L);
        assertThat(session.getCoverLetterId()).isEqualTo(2L);
        assertThat(session.getCoverLetterTitle()).isEqualTo("대표 자기소개서");
        assertThat(session.getCoverLetterContent()).isEqualTo("자기소개서 본문");
        assertThat(session.getResumeId()).isEqualTo(3L);
        assertThat(session.getResumeTitle()).isEqualTo("대표 이력서");
        assertThat(session.getResumeContent()).isEqualTo("이력서 본문");
        assertThat(session.getFailureCode()).isNull();
        assertThat(session.getCreatedAt()).isEqualTo(NOW);
        assertThat(session.getUpdatedAt()).isEqualTo(NOW);
    }


    @Test
    @DisplayName("대표 문서가 없어도 채용공고 스냅샷만으로 세션을 만든다")
    void createsSessionWithoutOptionalDocuments() {
        InterviewSession session = createSession(null, null);

        assertThat(session.getCoverLetterId()).isNull();
        assertThat(session.getCoverLetterTitle()).isNull();
        assertThat(session.getCoverLetterContent()).isNull();
        assertThat(session.getResumeId()).isNull();
        assertThat(session.getResumeTitle()).isNull();
        assertThat(session.getResumeContent()).isNull();
    }


    @Test
    @DisplayName("생성부터 완료까지 허용된 순서로 상태를 변경한다")
    void transitionsThroughInterviewLifecycle() {
        InterviewSession session = createSession(null, null);

        session.markReady(LATER);
        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.READY);
        assertThat(session.getUpdatedAt()).isEqualTo(LATER);

        session.start(LATER.plusMinutes(1));
        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);

        session.complete(LATER.plusMinutes(2));
        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.COMPLETED);
    }


    @Test
    @DisplayName("질문 생성 실패 후 실패 코드를 지우고 재시도한다")
    void retriesFailedGeneration() {
        InterviewSession session = createSession(null, null);

        session.fail("AI_TIMEOUT", LATER);
        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.FAILED);
        assertThat(session.getFailureCode()).isEqualTo("AI_TIMEOUT");

        session.retry(LATER.plusMinutes(1));
        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.GENERATING);
        assertThat(session.getFailureCode()).isNull();
    }


    @Test
    @DisplayName("현재 상태에서 허용되지 않는 상태 변경을 거부한다")
    void rejectsInvalidStateTransition() {
        InterviewSession session = createSession(null, null);

        assertThatIllegalStateException().isThrownBy(() -> session.start(LATER));

        session.markReady(LATER);
        assertThatIllegalStateException().isThrownBy(() -> session.fail("AI_ERROR", LATER));
        assertThatIllegalStateException().isThrownBy(() -> session.retry(LATER));
    }


    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("0 이하 원본 ID를 거부한다")
    void rejectsNonPositiveSourceId(long sourceId) {
        assertThatIllegalArgumentException().isThrownBy(() -> createSession(sourceId, null));
        assertThatIllegalArgumentException().isThrownBy(() -> createSession(null, sourceId));
    }


    @Test
    @DisplayName("선택 문서 ID와 스냅샷의 일부만 있으면 거부한다")
    void rejectsIncompleteOptionalSnapshot() {
        assertThatIllegalArgumentException().isThrownBy(() -> InterviewSession.create(
                user(), 1L, 2L, null,
                "회사", "백엔드 개발자", "Backend", "공고 본문",
                null, "자기소개서 본문", null, null, NOW
        ));

        assertThatIllegalArgumentException().isThrownBy(() -> InterviewSession.create(
                user(), 1L, null, 3L,
                "회사", "백엔드 개발자", "Backend", "공고 본문",
                null, null, "대표 이력서", " ", NOW
        ));
    }


    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    @DisplayName("필수 채용공고 스냅샷의 빈 값을 거부한다")
    void rejectsMissingJobPostingContent(String content) {
        if (content == null) {
            assertThatNullPointerException().isThrownBy(() -> createSessionWithJobContent(null));
            return;
        }

        assertThatIllegalArgumentException().isThrownBy(() -> createSessionWithJobContent(content));
    }


    private InterviewSession createSession(Long coverLetterId, Long resumeId) {
        return InterviewSession.create(
                user(),
                1L,
                coverLetterId,
                resumeId,
                "회사",
                "백엔드 개발자",
                "Backend",
                "공고 본문",
                coverLetterId == null ? null : "대표 자기소개서",
                coverLetterId == null ? null : "자기소개서 본문",
                resumeId == null ? null : "대표 이력서",
                resumeId == null ? null : "이력서 본문",
                NOW
        );
    }


    private void createSessionWithJobContent(String content) {
        InterviewSession.create(
                user(), 1L, null, null,
                "회사", "백엔드 개발자", "Backend", content,
                null, null, null, null, NOW
        );
    }


    private User user() {
        return User.createLocalUser("user@example.com", "{bcrypt}encoded", "사용자");
    }
}
