package com.interviewai.catalog;

import com.interviewai.company.dto.CreateCompanyRequest;
import com.interviewai.company.exception.CompanyHasJobPostingsException;
import com.interviewai.company.exception.CompanyNotFoundException;
import com.interviewai.company.service.CompanyService;
import com.interviewai.global.config.CatalogTimeConfig;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.jobposting.dto.CreateJobPostingRequest;
import com.interviewai.jobposting.enums.EmploymentType;
import com.interviewai.jobposting.service.JobPostingService;
import com.interviewai.rag.service.RagSourceChangeRegistrationService;
import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CompanyService.class, JobPostingService.class, AdminAuthorizationService.class, CatalogTimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogConcurrencyIntegrationTest extends MySqlIntegrationTest {

    @Autowired CompanyService companies;
    @Autowired JobPostingService postings;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean RagSourceChangeRegistrationService ragRegistrationService;
    private Long userId;
    private Long companyId;

    @BeforeEach
    void setUp() {
        User user = users.saveAndFlush(User.createLocalUser("race@example.com", "{bcrypt}encoded", "관리자"));
        userId = user.getId();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", userId);
        companyId = companies.create(userId.toString(),
                new CreateCompanyRequest("경합 기업", null, "소개", null, null)).id();
    }

    @AfterEach
    void cleanUp() {
        if (companyId != null) {
            jdbc.update("DELETE FROM job_postings WHERE company_id = ?", companyId);
            jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
        }
        if (userId != null) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    @DisplayName("독립 트랜잭션의 동시 관심 등록은 모두 성공하고 DB에 한 건만 남긴다")
    void registersFavoriteConcurrently() throws Exception {
        Runnable add = () -> companies.addFavorite(userId.toString(), companyId);
        assertThat(race(add, add)).containsExactly("OK", "OK");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM company_favorites WHERE user_id = ? AND company_id = ?",
                Integer.class, userId, companyId)).isEqualTo(1);
    }

    @Test
    @DisplayName("기업 삭제와 공고 생성이 경합하면 한 작업만 성공하고 고아 공고가 남지 않는다")
    void serializesCompanyDeletionAndPostingCreation() throws Exception {
        var outcomes = race(
                () -> companies.delete(userId.toString(), companyId),
                () -> postings.create(userId.toString(), new CreateJobPostingRequest(
                        companyId, "공고", "개발", EmploymentType.FULL_TIME,
                        null, "본문", null, null, null, false))
        );
        if (outcomes.getFirst().equals("OK")) {
            assertThat(outcomes).containsExactly("OK", "COMPANY_NOT_FOUND");
            assertThat(count("companies", "id")).isZero();
            assertThat(count("job_postings", "company_id")).isZero();
        } else {
            assertThat(outcomes).containsExactly("COMPANY_HAS_JOB_POSTINGS", "OK");
            assertThat(count("companies", "id")).isEqualTo(1);
            assertThat(count("job_postings", "company_id")).isEqualTo(1);
        }
    }

    private Integer count(String table, String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, companyId);
    }

    private List<String> race(Runnable first, Runnable second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var a = executor.submit(task(start, first));
                var b = executor.submit(task(start, second));
                return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private Callable<String> task(CyclicBarrier start, Runnable action) {
        return () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                action.run();
                return "OK";
            } catch (CompanyNotFoundException exception) {
                return "COMPANY_NOT_FOUND";
            } catch (CompanyHasJobPostingsException exception) {
                return "COMPANY_HAS_JOB_POSTINGS";
            }
        };
    }
}
