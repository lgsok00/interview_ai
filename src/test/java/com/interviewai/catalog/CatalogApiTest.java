package com.interviewai.catalog;

import com.interviewai.auth.handler.OAuth2AuthenticationFailureHandler;
import com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.interviewai.auth.service.GithubOAuth2UserService;
import com.interviewai.company.controller.AdminCompanyController;
import com.interviewai.company.controller.CompanyController;
import com.interviewai.company.entity.Company;
import com.interviewai.company.repository.CompanyFavoriteRepository;
import com.interviewai.company.repository.CompanyRepository;
import com.interviewai.company.service.CompanyService;
import com.interviewai.global.config.CatalogTimeConfig;
import com.interviewai.global.config.SecurityConfig;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.jobposting.controller.AdminJobPostingController;
import com.interviewai.jobposting.controller.JobPostingController;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.enums.EmploymentType;
import com.interviewai.jobposting.repository.JobPostingRepository;
import com.interviewai.jobposting.service.JobPostingService;
import com.interviewai.support.AuthFixtures;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CompanyController.class, AdminCompanyController.class,
        JobPostingController.class, AdminJobPostingController.class})
@Import({SecurityConfig.class, CatalogTimeConfig.class, AdminAuthorizationService.class,
        CompanyService.class, JobPostingService.class})
@TestPropertySource(properties = {
        "auth.jwt.secret=test-jwt-secret-that-is-at-least-32-bytes-long",
        "auth.jwt.access-token-expiration=1h",
        "auth.jwt.refresh-token-expiration=14d",
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret",
        "spring.security.oauth2.client.registration.google.scope[0]=openid",
        "spring.security.oauth2.client.registration.github.client-id=test-github-client-id",
        "spring.security.oauth2.client.registration.github.client-secret=test-github-client-secret"
})
class CatalogApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 0, 0);
    private static final String COMPANY_JSON = """
            {"name":"기업","description":"기업 소개"}
            """;
    private static final String POSTING_JSON = """
            {"companyId":10,"title":"개발자","jobRole":"백엔드",
             "employmentType":"FULL_TIME","description":"공고 본문"}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean CompanyRepository companies;
    @MockitoBean CompanyFavoriteRepository favorites;
    @MockitoBean JobPostingRepository postings;
    @MockitoBean UserRepository users;
    @MockitoBean OAuth2AuthenticationSuccessHandler successHandler;
    @MockitoBean OAuth2AuthenticationFailureHandler failureHandler;
    @MockitoBean GithubOAuth2UserService githubOAuth2UserService;

    private User user;
    private Company company;
    private JobPosting posting;

    @BeforeEach
    void setUp() {
        user = AuthFixtures.localUser();
        ReflectionTestUtils.setField(user, "id", 1L);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        company = Company.create("기업", "IT", "기업 소개", "https://example.com", "서울", NOW);
        ReflectionTestUtils.setField(company, "id", 10L);
        posting = JobPosting.create(company, "개발자", "백엔드", EmploymentType.FULL_TIME,
                "서울", "공고 본문", null, null, null, false, NOW);
        ReflectionTestUtils.setField(posting, "id", 20L);
    }

    @Test
    @DisplayName("관리자가 기업을 생성하면 조회 가능한 Location과 상세 응답을 반환한다")
    void createsCompanyWithLocation() throws Exception {
        admin();
        when(companies.save(any(Company.class))).thenAnswer(call -> {
            Company saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });
        mvc.perform(auth(post("/api/admin/companies")).contentType(MediaType.APPLICATION_JSON)
                        .content(COMPANY_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/companies/10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("기업"));
    }

    @Test
    @DisplayName("관리자가 공고를 생성하면 Location과 기본 모집 상태를 반환한다")
    void createsPostingWithLocation() throws Exception {
        admin();
        when(companies.findLockedById(10L)).thenReturn(Optional.of(company));
        when(postings.save(any(JobPosting.class))).thenAnswer(call -> {
            JobPosting saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 20L);
            return saved;
        });
        mvc.perform(auth(post("/api/admin/job-postings")).contentType(MediaType.APPLICATION_JSON)
                        .content(POSTING_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/job-postings/20"))
                .andExpect(jsonPath("$.companyId").value(10))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.manuallyClosed").value(false));
    }

    @Test
    @DisplayName("관심 기업 해제는 등록을 호출하지 않고 대상 사용자 설정만 삭제한다")
    void removesFavorite() throws Exception {
        // 잘못된 등록 경로도 끝까지 실행되도록 기업 조회를 준비한다.
        when(companies.findLockedById(10L)).thenReturn(Optional.of(company));
        for (int i = 0; i < 2; i++) {
            mvc.perform(auth(delete("/api/companies/10/favorite")))
                    .andExpect(status().isNoContent()).andExpect(content().string(""));
        }
        verify(favorites, times(2)).remove(1L, 10L);
        verify(favorites, never()).add(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("관심 기업 등록은 기업 잠금 후 현재 사용자의 설정을 저장한다")
    void addsFavoriteAfterCompanyLock() throws Exception {
        when(companies.findLockedById(10L)).thenReturn(Optional.of(company));
        mvc.perform(auth(put("/api/companies/10/favorite")))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        var order = inOrder(companies, favorites);
        order.verify(companies).findLockedById(10L);
        order.verify(favorites).add(eq(1L), eq(10L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("기업 검색과 관심 목록은 기본 페이징 및 각 목록의 정렬 정책을 사용한다")
    void searchesCompaniesAndFavorites() throws Exception {
        when(companies.search(eq(1L), eq("%"), any())).thenReturn(Page.empty());
        when(favorites.findSummaries(eq(1L), any())).thenReturn(Page.empty());
        mvc.perform(auth(get("/api/companies")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(auth(get("/api/companies/favorites")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        verify(companies).search(eq(1L), eq("%"), argThat(p ->
                p.getPageNumber() == 0 && p.getPageSize() == 20 && p.getSort().isSorted()));
        verify(favorites).findSummaries(eq(1L), argThat(p ->
                p.getPageSize() == 20 && p.getSort().isUnsorted()));
    }

    @Test
    @DisplayName("전체 및 기업별 공고 목록은 검색 필터와 페이징을 전달한다")
    void searchesPostings() throws Exception {
        when(companies.existsById(10L)).thenReturn(true);
        when(postings.search(eq(10L), eq("%개발%"), eq("OPEN"), any(), any()))
                .thenReturn(Page.empty());
        for (String path : new String[]{"/api/job-postings", "/api/companies/10/job-postings"}) {
            mvc.perform(auth(get(path)).param("companyId", "10").param("keyword", "개발")
                            .param("status", "OPEN").param("page", "1").param("size", "5"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        }
        verify(postings, times(2)).search(eq(10L), eq("%개발%"), eq("OPEN"), any(),
                argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 5));
    }

    @Test
    @DisplayName("기업 및 공고 상세는 본문과 기업 정보 및 관심 여부를 반환한다")
    void returnsDetails() throws Exception {
        when(companies.findById(10L)).thenReturn(Optional.of(company));
        when(favorites.existsByUserIdAndCompanyId(1L, 10L)).thenReturn(true);
        when(postings.findDetail(20L)).thenReturn(Optional.of(posting));
        mvc.perform(auth(get("/api/companies/10"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true))
                .andExpect(jsonPath("$.description").value("기업 소개"));
        mvc.perform(auth(get("/api/job-postings/20"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("기업"))
                .andExpect(jsonPath("$.description").value("공고 본문"));
    }

    @Test
    @DisplayName("기업 PUT은 필수 값을 정규화하고 생략된 선택 값을 삭제한다")
    void replacesCompany() throws Exception {
        admin();
        when(companies.findLockedById(10L)).thenReturn(Optional.of(company));
        mvc.perform(auth(put("/api/admin/companies/10")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" 수정 기업 \",\"description\":\"새 소개\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("수정 기업"));
        assertThat(company.getIndustry()).isNull();
        assertThat(company.getWebsiteUrl()).isNull();
        assertThat(company.getLocation()).isNull();
    }

    @Test
    @DisplayName("공고 PUT은 UTC로 시각을 변환하고 기업 잠금 후 공고를 갱신한다")
    void replacesPostingWithUtcTimes() throws Exception {
        admin();
        when(postings.findCompanyId(20L)).thenReturn(Optional.of(10L));
        when(companies.findLockedById(10L)).thenReturn(Optional.of(company));
        when(postings.findDetailForUpdate(20L)).thenReturn(Optional.of(posting));
        mvc.perform(auth(put("/api/admin/job-postings/20")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"수정","jobRole":"서버","employmentType":"CONTRACT",
                                 "description":"새 본문","manuallyClosed":true,
                                 "opensAt":"2026-09-07T09:00:00+09:00",
                                 "closesAt":"2026-09-08T09:00:00+09:00"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.opensAt").value("2026-09-07T00:00:00Z"));
        assertThat(posting.getLocation()).isNull();
        var order = inOrder(companies, postings);
        order.verify(postings).findCompanyId(20L);
        order.verify(companies).findLockedById(10L);
        order.verify(postings).findDetailForUpdate(20L);
    }

    @Test
    @DisplayName("관리자는 공고를 삭제한 뒤 비어 있는 기업을 삭제할 수 있다")
    void deletesPostingAndCompany() throws Exception {
        admin();
        when(postings.findCompanyId(20L)).thenReturn(Optional.of(10L));
        when(companies.findLockedById(10L)).thenReturn(Optional.of(company));
        when(postings.findDetailForUpdate(20L)).thenReturn(Optional.of(posting));
        mvc.perform(auth(delete("/api/admin/job-postings/20")))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(auth(delete("/api/admin/companies/10")))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(postings).delete(posting);
        verify(companies).delete(company);
    }

    @Test
    @DisplayName("공고가 있는 기업 삭제는 409이며 기업을 삭제하지 않는다")
    void rejectsCompanyDeletionWithPostings() throws Exception {
        admin();
        when(companies.findLockedById(10L)).thenReturn(Optional.of(company));
        when(postings.existsByCompanyId(10L)).thenReturn(true);
        mvc.perform(auth(delete("/api/admin/companies/10"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_HAS_JOB_POSTINGS"));
        verify(companies, never()).delete(any(Company.class));
    }

    @ParameterizedTest
    @CsvSource({"/api/companies/99,COMPANY_NOT_FOUND", "/api/job-postings/99,JOB_POSTING_NOT_FOUND",
            "/api/job-postings?companyId=99,COMPANY_NOT_FOUND",
            "/api/companies/99/job-postings,COMPANY_NOT_FOUND"})
    @DisplayName("없는 기업과 공고는 도메인별 404 오류를 반환한다")
    void returnsNotFound(String path, String code) throws Exception {
        mvc.perform(auth(get(path))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(code));
    }

    @ParameterizedTest
    @CsvSource({"/api/companies?page=-1,page", "/api/companies?size=0,size",
            "/api/companies?size=101,size", "/api/companies/no-number,companyId",
            "/api/companies/0,companyId", "/api/job-postings?status=INVALID,status",
            "/api/job-postings?page=abc,page", "/api/job-postings?companyId=-1,companyId"})
    @DisplayName("페이지 경계와 숫자 및 enum 변환 실패를 공통 필드 오류로 반환한다")
    void rejectsInvalidQuery(String path, String field) throws Exception {
        mvc.perform(auth(get(path))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors." + field).isString());
        verifyNoInteractions(companies, favorites, postings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "{\"name\":\" \",\"description\":\"소개\"}"})
    @DisplayName("잘못된 JSON과 필수 값 누락은 400이며 저장하지 않는다")
    void rejectsInvalidCompanyBody(String body) throws Exception {
        mvc.perform(auth(post("/api/admin/companies")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(companies, favorites, postings);
    }

    @ParameterizedTest
    @CsvSource({"100,20000,201", "101,20000,400", "100,20001,400"})
    @DisplayName("기업 요청은 이름과 본문의 최대 길이를 허용하고 초과 입력은 저장하지 않는다")
    void validatesCompanyLengths(int nameLength, int bodyLength, int expectedStatus) throws Exception {
        admin();
        when(companies.save(any(Company.class))).thenReturn(company);
        mvc.perform(auth(post("/api/admin/companies")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "가".repeat(nameLength)
                                + "\",\"description\":\"" + "나".repeat(bodyLength) + "\"}"))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 400) {
            verify(companies, never()).save(any(Company.class));
        } else {
            verify(companies).save(any(Company.class));
        }
    }

    @ParameterizedTest
    @CsvSource({"200,30000,201", "201,30000,400", "200,30001,400"})
    @DisplayName("공고 요청은 제목과 본문의 최대 길이를 허용하고 초과 입력은 저장하지 않는다")
    void validatesPostingLengths(int titleLength, int bodyLength, int expectedStatus) throws Exception {
        admin();
        when(companies.findLockedById(10L)).thenReturn(Optional.of(company));
        when(postings.save(any(JobPosting.class))).thenReturn(posting);
        String body = POSTING_JSON.replace("개발자", "가".repeat(titleLength))
                .replace("공고 본문", "나".repeat(bodyLength));
        mvc.perform(auth(post("/api/admin/job-postings")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 400) {
            verify(postings, never()).save(any(JobPosting.class));
        } else {
            verify(postings).save(any(JobPosting.class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"employmentType\":\"SECRET_INVALID_ENUM\"",
            "\"opensAt\":\"SECRET_INVALID_DATE\""})
    @DisplayName("본문 enum과 시각 변환 실패는 내부 입력값을 노출하지 않는다")
    void rejectsMalformedPostingValues(String field) throws Exception {
        mvc.perform(auth(post("/api/admin/job-postings")).contentType(MediaType.APPLICATION_JSON)
                        .content("{" + field + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(content().string(not(containsString("SECRET_INVALID"))));
        verifyNoInteractions(postings);
    }

    @Test
    @DisplayName("공고 수정에서는 수동 마감 여부가 필수다")
    void requiresManualClosedOnUpdate() throws Exception {
        mvc.perform(auth(put("/api/admin/job-postings/20")).contentType(MediaType.APPLICATION_JSON)
                        .content(POSTING_JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.manuallyClosed").isString());
        verifyNoInteractions(postings);
    }

    @Test
    @DisplayName("종료 시각이 시작과 같으면 400이며 공고를 저장하지 않는다")
    void rejectsInvalidPeriod() throws Exception {
        admin();
        String body = POSTING_JSON.strip().replace("\"description\":\"공고 본문\"",
                "\"description\":\"공고 본문\",\"opensAt\":\"2026-09-07T00:00:00Z\","
                        + "\"closesAt\":\"2026-09-07T00:00:00Z\"");
        mvc.perform(auth(post("/api/admin/job-postings")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.closesAt").isString());
        verifyNoInteractions(companies, postings);
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/admin/companies", "PUT,/api/admin/companies/10", "DELETE,/api/admin/companies/10",
            "POST,/api/admin/job-postings", "PUT,/api/admin/job-postings/20", "DELETE,/api/admin/job-postings/20"})
    @DisplayName("JWT ADMIN claim이 있어도 DB 역할이 USER이면 모든 관리자 쓰기를 거부한다")
    void rejectsStaleAdminClaim(String method, String path) throws Exception {
        String body = path.contains("job-postings")
                ? POSTING_JSON.replace("\"companyId\":10", "\"manuallyClosed\":false,\"companyId\":10")
                : COMPANY_JSON;
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path)
                        .with(jwt().jwt(token -> token.subject("1").claim("role", "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(companies, favorites, postings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "9223372036854775808"})
    @DisplayName("잘못된 JWT subject는 DB 조회 전에 거부한다")
    void rejectsInvalidSubject(String subject) throws Exception {
        mvc.perform(get("/api/companies").with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        verify(users, never()).findById(anyLong());
        verifyNoInteractions(companies);
    }

    @Test
    @DisplayName("탈퇴 사용자의 유효한 JWT도 DB 사용자 검사에서 거부한다")
    void rejectsDeletedUser() throws Exception {
        when(users.findById(1L)).thenReturn(Optional.empty());
        mvc.perform(auth(get("/api/job-postings"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        verifyNoInteractions(postings);
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/companies", "GET,/api/companies/10", "GET,/api/companies/favorites",
            "GET,/api/companies/10/job-postings", "PUT,/api/companies/10/favorite",
            "DELETE,/api/companies/10/favorite", "GET,/api/job-postings", "GET,/api/job-postings/20",
            "POST,/api/admin/companies", "PUT,/api/admin/companies/10", "DELETE,/api/admin/companies/10",
            "POST,/api/admin/job-postings", "PUT,/api/admin/job-postings/20", "DELETE,/api/admin/job-postings/20"})
    @DisplayName("기업 및 채용공고의 모든 endpoint는 인증을 요구한다")
    void requiresAuthentication(String method, String path) throws Exception {
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(companies, favorites, postings);
    }

    private void admin() {
        ReflectionTestUtils.setField(user, "role", UserRole.ADMIN);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(token -> token.subject("1").claim("role", "USER")));
    }
}
