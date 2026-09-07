package com.interviewai.catalog;

import com.interviewai.company.dto.CompanyPageResponse;
import com.interviewai.company.entity.Company;
import com.interviewai.company.repository.CompanyFavoriteRepository;
import com.interviewai.company.repository.CompanyRepository;
import com.interviewai.global.validation.CatalogInput;
import com.interviewai.jobposting.dto.JobPostingPageResponse;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.enums.EmploymentType;
import com.interviewai.jobposting.enums.JobPostingStatus;
import com.interviewai.jobposting.repository.JobPostingRepository;
import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class CatalogRepositoryIntegrationTest extends MySqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 0, 0);
    @Autowired CompanyRepository companies;
    @Autowired CompanyFavoriteRepository favorites;
    @Autowired JobPostingRepository postings;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway V5 기업 및 채용공고 migration이 적용된다")
    void appliesMigration() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = TRUE",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("기업 검색은 특수문자를 문자 그대로 찾고 사용자별 관심 여부를 구분한다")
    void searchesEscapedCompanyNames() {
        User owner = user("owner");
        User other = user("other");
        Company target = company("기업!%_");
        company("기업ABC");
        favorites.add(owner.getId(), target.getId(), NOW);
        String pattern = CatalogInput.pattern("!%_");
        var owned = CompanyPageResponse.from(companies.search(owner.getId(), pattern, CatalogInput.page(0, 20)));
        assertThat(owned.items()).hasSize(1);
        assertThat(owned.items().getFirst().id()).isEqualTo(target.getId());
        assertThat(owned.items().getFirst().favorite()).isTrue();
        assertThat(companies.search(other.getId(), pattern, CatalogInput.page(0, 20))
                .getContent().getFirst().getFavorite()).isFalse();
    }

    @Test
    @DisplayName("생성 시각이 같은 기업 목록은 ID 내림차순으로 페이징한다")
    void pagesCompaniesDeterministically() {
        User user = user("owner");
        Company first = company("첫 기업");
        Company second = company("둘째 기업");
        var page = companies.search(user.getId(), "%", CatalogInput.page(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent().getFirst().getId()).isEqualTo(second.getId());
        assertThat(companies.search(user.getId(), "%", CatalogInput.page(1, 1))
                .getContent().getFirst().getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("관심 중복 등록은 기존 시각을 유지하며 해제는 다른 사용자에게 영향을 주지 않는다")
    void keepsFavoritesIdempotentAndIsolated() {
        User owner = user("owner");
        User other = user("other");
        Company first = company("첫 기업");
        Company second = company("둘째 기업");
        favorites.add(owner.getId(), first.getId(), NOW);
        favorites.add(owner.getId(), second.getId(), NOW.plusSeconds(1));
        favorites.add(owner.getId(), first.getId(), NOW.plusSeconds(2));
        favorites.add(other.getId(), first.getId(), NOW);
        assertThat(favorites.findSummaries(owner.getId(), PageRequest.of(0, 20)).getContent())
                .extracting(CompanyRepository.SummaryRow::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(favorites.count()).isEqualTo(3);
        favorites.remove(owner.getId(), first.getId());
        favorites.remove(owner.getId(), first.getId());
        assertThat(favorites.existsByUserIdAndCompanyId(owner.getId(), first.getId())).isFalse();
        assertThat(favorites.existsByUserIdAndCompanyId(other.getId(), first.getId())).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"-1,SCHEDULED", "0,OPEN", "1,OPEN", "9,OPEN", "10,CLOSED", "11,CLOSED"})
    @DisplayName("시작 포함 종료 제외 경계에서 DB 검색 상태와 엔티티 상태가 일치한다")
    void matchesStatusBoundaries(long seconds, JobPostingStatus expected) {
        Company company = company("기업");
        JobPosting posting = posting(company, "개발!%_", NOW, NOW.plusSeconds(10), false);
        LocalDateTime point = NOW.plusSeconds(seconds);
        var result = JobPostingPageResponse.from(postings.search(company.getId(), CatalogInput.pattern("!%_"),
                expected.name(), point, CatalogInput.page(0, 20)));
        assertThat(posting.statusAt(point)).isEqualTo(expected);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().status()).isEqualTo(expected);
        assertThat(result.items().getFirst().companyName()).isEqualTo("기업");
        assertThat(result.items().getFirst().employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        for (JobPostingStatus status : JobPostingStatus.values()) {
            if (status != expected) {
                assertThat(postings.search(company.getId(), "%", status.name(), point, CatalogInput.page(0, 20)))
                        .isEmpty();
            }
        }
    }

    @Test
    @DisplayName("수동 마감이 미래 시작보다 우선하고 기간 없는 공고는 모집 중이다")
    void prioritizesManualClosingAndAllowsUnboundedPeriod() {
        Company company = company("기업");
        JobPosting closed = posting(company, "마감", NOW.plusDays(1), null, true);
        JobPosting open = posting(company, "상시", null, null, false);
        assertThat(closed.statusAt(NOW)).isEqualTo(JobPostingStatus.CLOSED);
        assertThat(open.statusAt(NOW)).isEqualTo(JobPostingStatus.OPEN);
        assertThat(postings.search(null, "%", "CLOSED", NOW, CatalogInput.page(0, 20)).getContent())
                .extracting(JobPostingRepository.SummaryRow::getId).containsExactly(closed.getId());
        assertThat(postings.search(null, "%", "OPEN", NOW, CatalogInput.page(0, 20)).getContent())
                .extracting(JobPostingRepository.SummaryRow::getId).containsExactly(open.getId());
    }

    @Test
    @DisplayName("공고 목록은 기업 필터와 정렬 및 count 쿼리를 동일하게 적용한다")
    void filtersAndPagesPostings() {
        Company first = company("첫 기업");
        Company second = company("다른 기업");
        JobPosting older = posting(first, "첫 공고", null, null, false);
        JobPosting newer = posting(first, "둘째 공고", null, null, false);
        posting(second, "다른 공고", null, null, false);
        var page = postings.search(first.getId(), "%", null, NOW, CatalogInput.page(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent().getFirst().getId()).isEqualTo(newer.getId());
        assertThat(postings.search(first.getId(), "%", null, NOW, CatalogInput.page(1, 1))
                .getContent().getFirst().getId()).isEqualTo(older.getId());
        assertThat(postings.findDetail(newer.getId()).orElseThrow().getCompany().getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("공고가 있는 기업 삭제는 DB 외래 키도 거부한다")
    void restrictsCompanyDeletion() {
        Company company = company("기업");
        posting(company, "공고", null, null, false);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM companies WHERE id = ?", company.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB는 같은 시작 종료 시각을 거부한다")
    void rejectsInvalidPeriodInDatabase() {
        JobPosting posting = posting(company("기업"), "공고", null, null, false);
        assertCheckViolation(() -> jdbc.update(
                "UPDATE job_postings SET opens_at = ?, closes_at = ? WHERE id = ?", NOW, NOW, posting.getId()),
                "chk_job_postings_period");
    }

    @Test
    @DisplayName("DB는 정의하지 않은 고용 형태를 거부한다")
    void rejectsInvalidEmploymentType() {
        JobPosting posting = posting(company("기업"), "공고", null, null, false);
        assertCheckViolation(() -> jdbc.update(
                "UPDATE job_postings SET employment_type = 'INVALID' WHERE id = ?", posting.getId()),
                "chk_job_postings_employment_type");
    }

    @Test
    @DisplayName("DB는 관심 기업의 사용자 기업 중복을 직접 삽입할 때 거부한다")
    void rejectsDuplicateFavoriteInDatabase() {
        User user = user("owner");
        Company company = company("기업");
        favorites.add(user.getId(), company.getId(), NOW);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO company_favorites (user_id, company_id, created_at) VALUES (?, ?, ?)",
                user.getId(), company.getId(), NOW)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("기업 및 사용자 삭제는 해당 관심 설정만 cascade 삭제한다")
    void cascadesFavorites() {
        User owner = user("owner");
        User other = user("other");
        Company first = company("첫 기업");
        Company second = company("둘째 기업");
        favorites.add(owner.getId(), first.getId(), NOW);
        favorites.add(owner.getId(), second.getId(), NOW);
        favorites.add(other.getId(), second.getId(), NOW);
        jdbc.update("DELETE FROM companies WHERE id = ?", first.getId());
        assertThat(favorites.count()).isEqualTo(2);
        jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
        assertThat(favorites.count()).isEqualTo(1);
        assertThat(favorites.existsByUserIdAndCompanyId(other.getId(), second.getId())).isTrue();
        assertThat(companies.existsById(second.getId())).isTrue();
    }

    private void assertCheckViolation(ThrowingCallable action, String constraintName) {
        assertThatThrownBy(action).isInstanceOfSatisfying(DataAccessException.class, exception ->
                assertThat(exception.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, sql -> {
                    assertThat(sql.getErrorCode()).isEqualTo(3819);
                    assertThat(sql.getSQLState()).isEqualTo("HY000");
                    assertThat(sql.getMessage()).contains(constraintName);
                }));
    }

    private User user(String name) {
        return users.saveAndFlush(User.createLocalUser(name + "@example.com", "{bcrypt}encoded-password", name));
    }

    private Company company(String name) {
        return companies.saveAndFlush(Company.create(name, "IT", "소개", null, null, NOW));
    }

    private JobPosting posting(Company company, String title, LocalDateTime opens, LocalDateTime closes, boolean closed) {
        return postings.saveAndFlush(JobPosting.create(company, title, "개발", EmploymentType.FULL_TIME,
                null, "본문", null, opens, closes, closed, NOW));
    }
}
