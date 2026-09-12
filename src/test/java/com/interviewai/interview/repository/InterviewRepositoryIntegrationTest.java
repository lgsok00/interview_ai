package com.interviewai.interview.repository;

import com.interviewai.interview.entity.InterviewQuestion;
import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.QuestionGenerationSource;
import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class InterviewRepositoryIntegrationTest extends MySqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 10, 0);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InterviewSessionRepository sessionRepository;
    @Autowired
    private InterviewQuestionRepository questionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;


    @Test
    @DisplayName("Flyway V10 면접 세션 migration이 적용된다")
    void appliesInterviewMigration() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version = '10' AND success = TRUE
                        """,
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }


    @Test
    @DisplayName("원본 FK 없이 생성 시점 스냅샷을 저장한다")
    void persistsSessionSnapshotWithoutSourceForeignKeys() {
        User user = saveUser("snapshot@example.com");
        InterviewSession session = sessionRepository.saveAndFlush(createSession(user));
        entityManager.clear();

        InterviewSession found = sessionRepository.findById(session.getId()).orElseThrow();

        assertThat(found.getJobPostingId()).isEqualTo(999L);
        assertThat(found.getCoverLetterId()).isEqualTo(998L);
        assertThat(found.getResumeId()).isEqualTo(997L);
        assertThat(found.getCompanyName()).isEqualTo("스냅샷 회사");
        assertThat(found.getJobPostingContent()).isEqualTo("삭제 후에도 보존할 공고 본문");
        assertThat(found.getCoverLetterContent()).isEqualTo("삭제 후에도 보존할 자기소개서");
        assertThat(found.getResumeContent()).isEqualTo("삭제 후에도 보존할 이력서");
    }


    @Test
    @DisplayName("세션 질문을 순서대로 조회한다")
    void findsQuestionsInSequenceOrder() {
        InterviewSession session = sessionRepository.saveAndFlush(createSession(saveUser("order@example.com")));
        questionRepository.save(question(session, 2, "두 번째 질문"));
        questionRepository.save(question(session, 1, "첫 번째 질문"));
        questionRepository.flush();
        entityManager.clear();

        assertThat(questionRepository.findAllBySession_IdOrderBySequenceNumberAsc(session.getId()))
                .extracting(InterviewQuestion::getSequenceNumber, InterviewQuestion::getContent)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "첫 번째 질문"),
                        org.assertj.core.groups.Tuple.tuple(2, "두 번째 질문")
                );
    }


    @Test
    @DisplayName("같은 세션의 중복 질문 순서를 거부한다")
    void rejectsDuplicateQuestionSequence() {
        InterviewSession session = sessionRepository.saveAndFlush(createSession(saveUser("duplicate@example.com")));
        questionRepository.saveAndFlush(question(session, 1, "첫 번째 질문"));

        assertThatThrownBy(() -> questionRepository.saveAndFlush(question(session, 1, "중복 질문")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }


    @Test
    @DisplayName("세션을 삭제하면 질문도 cascade 삭제한다")
    void cascadesQuestionsWhenSessionIsDeleted() {
        InterviewSession session = sessionRepository.saveAndFlush(createSession(saveUser("session-delete@example.com")));
        questionRepository.saveAndFlush(question(session, 1, "질문"));
        Long sessionId = session.getId();
        entityManager.clear();

        sessionRepository.delete(sessionRepository.findById(sessionId).orElseThrow());
        sessionRepository.flush();

        assertThat(sessionRepository.count()).isZero();
        assertThat(questionRepository.count()).isZero();
    }


    @Test
    @DisplayName("사용자를 삭제하면 면접 세션과 질문을 모두 cascade 삭제한다")
    void cascadesInterviewDataWhenUserIsDeleted() {
        User user = saveUser("user-delete@example.com");
        InterviewSession session = sessionRepository.saveAndFlush(createSession(user));
        questionRepository.saveAndFlush(question(session, 1, "질문"));
        Long userId = user.getId();
        entityManager.clear();

        userRepository.delete(userRepository.findById(userId).orElseThrow());
        userRepository.flush();

        assertThat(sessionRepository.count()).isZero();
        assertThat(questionRepository.count()).isZero();
    }


    private User saveUser(String email) {
        return userRepository.saveAndFlush(
                User.createLocalUser(email, "{bcrypt}encoded-password", "사용자")
        );
    }


    private InterviewSession createSession(User user) {
        return InterviewSession.create(
                user,
                999L,
                998L,
                997L,
                "스냅샷 회사",
                "백엔드 개발자",
                "Backend",
                "삭제 후에도 보존할 공고 본문",
                "대표 자기소개서",
                "삭제 후에도 보존할 자기소개서",
                "대표 이력서",
                "삭제 후에도 보존할 이력서",
                NOW
        );
    }


    private InterviewQuestion question(InterviewSession session, int sequence, String content) {
        return InterviewQuestion.create(
                session,
                sequence,
                InterviewQuestionType.TECHNICAL,
                QuestionGenerationSource.AI,
                content,
                "RAG context",
                NOW
        );
    }
}
