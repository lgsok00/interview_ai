package com.interviewai.resume.repository;

import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.entity.ResumeRepresentative;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ResumeRepositoryIntegrationTest extends MySqlIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ResumeRepository resumeRepository;
    @Autowired
    private ResumeRepresentativeRepository representativeRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;


    @Test
    @DisplayName("Flyway V4 이력서 migration이 적용된다")
    void appliesResumeMigration() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version = '4' AND success = TRUE
                        """,
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }


    @Test
    @DisplayName("사용자별 수정 시각 내림차순으로 이력서를 조회한다")
    void findsResumesOwnedByUser() {
        User user = saveUser("user@example.com", "사용자");
        Resume resume = resumeRepository.saveAndFlush(createResume(user));

        assertThat(resumeRepository.findByIdAndUser_Id(resume.getId(), user.getId()))
                .contains(resume);
        assertThat(resumeRepository.findStorageKeysByUserId(user.getId()))
                .containsExactly("1/resume.pdf");
    }


    @Test
    @DisplayName("다른 사용자의 이력서를 대표로 지정하면 복합 외래 키가 거부한다")
    void rejectsRepresentativeOwnedByAnotherUser() {
        User owner = saveUser("owner@example.com", "소유자");
        User other = saveUser("other@example.com", "다른 사용자");
        Resume resume = resumeRepository.saveAndFlush(createResume(owner));

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO resume_representatives
                            (user_id, resume_id, created_at)
                        VALUES (?, ?, NOW(6))
                        """,
                other.getId(),
                resume.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }


    @Test
    @DisplayName("이력서를 삭제하면 대표 설정도 cascade 삭제한다")
    void cascadesRepresentativeWhenResumeIsDeleted() {
        User user = saveUser("user@example.com", "사용자");
        Resume resume = resumeRepository.saveAndFlush(createResume(user));
        representativeRepository.saveAndFlush(ResumeRepresentative.create(user.getId(), resume));
        Long resumeId = resume.getId();
        entityManager.clear();

        resumeRepository.delete(resumeRepository.findById(resumeId).orElseThrow());
        resumeRepository.flush();

        assertThat(resumeRepository.count()).isZero();
        assertThat(representativeRepository.count()).isZero();
    }


    @Test
    @DisplayName("사용자를 삭제하면 이력서와 대표 설정을 모두 cascade 삭제한다")
    void cascadesResumeDataWhenUserIsDeleted() {
        User user = saveUser("user@example.com", "사용자");
        Resume resume = resumeRepository.saveAndFlush(createResume(user));
        representativeRepository.saveAndFlush(ResumeRepresentative.create(user.getId(), resume));
        Long userId = user.getId();
        entityManager.clear();

        userRepository.delete(userRepository.findById(userId).orElseThrow());
        userRepository.flush();

        assertThat(resumeRepository.count()).isZero();
        assertThat(representativeRepository.count()).isZero();
    }


    private User saveUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.createLocalUser(
                email, "{bcrypt}encoded-password", nickname
        ));
    }


    private Resume createResume(User user) {
        Resume resume = Resume.create(
                user, "이력서", "resume.pdf", "1/resume.pdf",
                "application/pdf", 100, "a".repeat(64)
        );
        resume.completeExtraction("resume text");
        return resume;
    }
}
