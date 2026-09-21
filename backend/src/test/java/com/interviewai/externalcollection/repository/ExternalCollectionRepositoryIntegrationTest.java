package com.interviewai.externalcollection.repository;

import com.interviewai.externalcollection.entity.ExternalCollectionRequest;
import com.interviewai.externalcollection.entity.ExternalCollectionSnapshot;
import com.interviewai.externalcollection.enums.ExternalCollectionKind;
import com.interviewai.externalcollection.enums.ExternalCollectionRequestStatus;
import com.interviewai.externalcollection.enums.ExternalCollectionSnapshotStatus;
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
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ExternalCollectionRepositoryIntegrationTest extends MySqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 12, 0);
    private static final String SHA256 = "b".repeat(64);
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ExternalCollectionRequestRepository requestRepository;
    @Autowired
    private ExternalCollectionSnapshotRepository snapshotRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Flyway V18과 V19 외부 수집 migration이 적용된다")
    void appliesExternalCollectionMigrations() {
        Integer count = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version IN ('18', '19') AND success = TRUE
                        """,
                Integer.class
        );

        assertThat(count).isEqualTo(2);
    }


    @Test
    @DisplayName("수집 요청과 성공 스냅샷 JSON을 저장하고 조회한다")
    void persistsRequestAndSuccessSnapshot() {
        ExternalCollectionRequest request = requestRepository.saveAndFlush(request("persist@example.com"));
        ExternalCollectionSnapshot snapshot = snapshotRepository.saveAndFlush(success(request, 1));
        entityManager.clear();

        ExternalCollectionRequest foundRequest = requestRepository.findById(request.getId()).orElseThrow();
        ExternalCollectionSnapshot foundSnapshot = snapshotRepository
                .findByIdAndRequest_Id(snapshot.getId(), request.getId())
                .orElseThrow();

        assertThat(foundRequest.getStatus()).isEqualTo(ExternalCollectionRequestStatus.PENDING);
        assertThat(foundSnapshot.getStatus()).isEqualTo(ExternalCollectionSnapshotStatus.SUCCEEDED);
        assertThat(objectMapper.readTree(foundSnapshot.getCandidateJson()))
                .isEqualTo(objectMapper.readTree("{\"title\":\"백엔드 개발자\"}"));
        assertThat(objectMapper.readTree(foundSnapshot.getEvidenceJson()))
                .isEqualTo(objectMapper.readTree("{\"title\":[0,10]}"));
        assertThat(snapshotRepository.findAllByRequest_IdOrderByCollectedAtDescIdDesc(request.getId()))
                .extracting(ExternalCollectionSnapshot::getId)
                .containsExactly(snapshot.getId());
    }


    @Test
    @DisplayName("V19는 한 요청의 아홉 번 수집 시도 이력을 보존한다")
    void persistsNineAttemptSnapshots() {
        ExternalCollectionRequest request = requestRepository.saveAndFlush(request("nine-attempts@example.com"));

        for (int attempt = 1; attempt <= 9; attempt++) {
            snapshotRepository.save(success(request, attempt));
        }
        snapshotRepository.flush();
        entityManager.clear();

        assertThat(snapshotRepository.findAllByRequest_IdOrderByCollectedAtDescIdDesc(request.getId()))
                .hasSize(9)
                .extracting(ExternalCollectionSnapshot::getAttemptNumber)
                .containsExactly(9, 8, 7, 6, 5, 4, 3, 2, 1);
    }


    @Test
    @DisplayName("열 번째 수집 스냅샷을 DB가 거부한다")
    void rejectsTenthAttempt() {
        ExternalCollectionRequest request = requestRepository.saveAndFlush(request("tenth-attempt@example.com"));

        assertThatThrownBy(() -> snapshotRepository.saveAndFlush(success(request, 10)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(java.sql.SQLException.class);
    }


    @Test
    @DisplayName("같은 요청의 중복 시도 번호를 DB가 거부한다")
    void rejectsDuplicateAttemptNumber() {
        ExternalCollectionRequest request = requestRepository.saveAndFlush(request("duplicate-attempt@example.com"));
        snapshotRepository.saveAndFlush(success(request, 1));

        ExternalCollectionSnapshot duplicate = ExternalCollectionSnapshot.success(
                request,
                1,
                UUID.fromString("10000000-0000-0000-0000-000000000001").toString(),
                "https://careers.example.com/jobs/1",
                200,
                "text/html",
                SHA256,
                "중복 시도",
                "{}",
                "{}",
                "external-collection-v1",
                NOW.plusMinutes(1)
        );

        assertThatThrownBy(() -> snapshotRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(java.sql.SQLException.class);
    }


    private ExternalCollectionRequest request(String email) {
        User user = userRepository.saveAndFlush(
                User.createLocalUser(email, "{bcrypt}encoded", "관리자")
        );

        return ExternalCollectionRequest.create(
                ExternalCollectionKind.JOB_POSTING,
                "https://careers.example.com/jobs/1",
                "https://careers.example.com/jobs/1",
                user,
                NOW
        );
    }


    private ExternalCollectionSnapshot success(ExternalCollectionRequest request, int attempt) {
        return ExternalCollectionSnapshot.success(
                request,
                attempt,
                new UUID(0L, attempt).toString(),
                "https://careers.example.com/jobs/" + attempt,
                200,
                "text/html",
                SHA256,
                "백엔드 개발자 채용 공고 " + attempt,
                "{\"title\":\"백엔드 개발자\"}",
                "{\"title\":[0,10]}",
                "external-collection-v1",
                NOW.plusMinutes(attempt)
        );
    }
}
