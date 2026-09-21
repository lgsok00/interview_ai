package com.interviewai.coverletter.repository;

import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterRepresentative;
import com.interviewai.coverletter.entity.CoverLetterVersion;
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
class CoverLetterRepositoryIntegrationTest extends MySqlIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CoverLetterRepository coverLetterRepository;
    @Autowired
    private CoverLetterVersionRepository versionRepository;
    @Autowired
    private CoverLetterRepresentativeRepository representativeRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;


    @Test
    @DisplayName("Flyway V3 자기소개서 migration이 적용된다")
    void appliesCoverLetterMigration() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version = '3' AND success = TRUE
                        """,
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }


    @Test
    @DisplayName("자기소개서와 여러 버전을 저장하고 최신 번호 순으로 조회한다")
    void savesAndFindsVersionsInDescendingOrder() {
        User user = saveUser("user@example.com", "사용자");
        CoverLetter coverLetter = coverLetterRepository.saveAndFlush(
                CoverLetter.create(user, "수정 제목")
        );
        versionRepository.saveAndFlush(
                CoverLetterVersion.createInitial(coverLetter, "최초 제목", "최초 본문")
        );
        int secondVersion = coverLetter.addVersion("수정 제목");
        versionRepository.saveAndFlush(
                CoverLetterVersion.create(coverLetter, secondVersion, "수정 제목", "수정 본문")
        );

        assertThat(versionRepository.findAllByCoverLetter_IdOrderByVersionNumberDesc(coverLetter.getId()))
                .extracting(CoverLetterVersion::getVersionNumber)
                .containsExactly(2, 1);
        assertThat(coverLetterRepository.findByIdAndUser_Id(coverLetter.getId(), user.getId()))
                .get()
                .extracting(CoverLetter::getCurrentVersionNumber)
                .isEqualTo(2);
    }


    @Test
    @DisplayName("사용자는 하나의 대표 자기소개서만 가지며 대표 문서를 교체할 수 있다")
    void keepsOneRepresentativePerUser() {
        User user = saveUser("user@example.com", "사용자");
        CoverLetter first = saveCoverLetter(user, "첫 번째");
        CoverLetter second = saveCoverLetter(user, "두 번째");
        CoverLetterRepresentative representative = representativeRepository.saveAndFlush(
                CoverLetterRepresentative.create(user.getId(), first)
        );

        representative.changeCoverLetter(second);
        representativeRepository.flush();

        CoverLetterRepresentative found = representativeRepository.findById(user.getId()).orElseThrow();
        assertThat(found.getCoverLetter().getId()).isEqualTo(second.getId());
        assertThat(representativeRepository.count()).isEqualTo(1);
    }


    @Test
    @DisplayName("다른 사용자의 자기소개서를 대표 문서로 지정하면 복합 외래 키가 거부한다")
    void rejectsRepresentativeOwnedByAnotherUser() {
        User owner = saveUser("owner@example.com", "소유자");
        User otherUser = saveUser("other@example.com", "다른 사용자");
        CoverLetter ownersCoverLetter = saveCoverLetter(owner, "소유자의 자기소개서");

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO cover_letter_representatives
                            (user_id, cover_letter_id, created_at)
                        VALUES (?, ?, NOW(6))
                        """,
                otherUser.getId(),
                ownersCoverLetter.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }


    @Test
    @DisplayName("자기소개서를 삭제하면 버전과 대표 설정도 함께 삭제한다")
    void cascadesVersionsAndRepresentativeWhenCoverLetterIsDeleted() {
        User user = saveUser("user@example.com", "사용자");
        CoverLetter coverLetter = saveCoverLetter(user, "삭제할 자기소개서");
        versionRepository.saveAndFlush(
                CoverLetterVersion.createInitial(coverLetter, "삭제할 자기소개서", "본문")
        );
        representativeRepository.saveAndFlush(
                CoverLetterRepresentative.create(user.getId(), coverLetter)
        );

        Long coverLetterId = coverLetter.getId();
        entityManager.clear();

        CoverLetter managedCoverLetter = coverLetterRepository.findById(coverLetterId).orElseThrow();
        coverLetterRepository.delete(managedCoverLetter);
        coverLetterRepository.flush();

        assertThat(versionRepository.count()).isZero();
        assertThat(representativeRepository.count()).isZero();
    }


    @Test
    @DisplayName("사용자를 삭제하면 소유한 자기소개서와 버전 및 대표 설정을 모두 삭제한다")
    void cascadesAllCoverLetterDataWhenUserIsDeleted() {
        User user = saveUser("user@example.com", "사용자");
        CoverLetter coverLetter = saveCoverLetter(user, "탈퇴 사용자 자기소개서");
        versionRepository.saveAndFlush(
                CoverLetterVersion.createInitial(coverLetter, "탈퇴 사용자 자기소개서", "본문")
        );
        representativeRepository.saveAndFlush(
                CoverLetterRepresentative.create(user.getId(), coverLetter)
        );

        Long userId = user.getId();
        entityManager.clear();

        User managedUser = userRepository.findById(userId).orElseThrow();
        userRepository.delete(managedUser);
        userRepository.flush();

        assertThat(coverLetterRepository.count()).isZero();
        assertThat(versionRepository.count()).isZero();
        assertThat(representativeRepository.count()).isZero();
    }


    private User saveUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.createLocalUser(
                email,
                "{bcrypt}encoded-password",
                nickname
        ));
    }


    private CoverLetter saveCoverLetter(User user, String title) {
        return coverLetterRepository.saveAndFlush(CoverLetter.create(user, title));
    }
}
