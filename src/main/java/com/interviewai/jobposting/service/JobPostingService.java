package com.interviewai.jobposting.service;

import com.interviewai.company.entity.Company;
import com.interviewai.company.exception.CompanyNotFoundException;
import com.interviewai.company.repository.CompanyRepository;
import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.global.validation.CatalogInput;
import com.interviewai.jobposting.dto.CreateJobPostingRequest;
import com.interviewai.jobposting.dto.JobPostingPageResponse;
import com.interviewai.jobposting.dto.JobPostingResponse;
import com.interviewai.jobposting.dto.UpdateJobPostingRequest;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.enums.EmploymentType;
import com.interviewai.jobposting.enums.JobPostingStatus;
import com.interviewai.jobposting.exception.JobPostingNotFoundException;
import com.interviewai.jobposting.repository.JobPostingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@Transactional(readOnly = true)
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final CompanyRepository companyRepository;
    private final AdminAuthorizationService authorizationService;
    private final Clock catalogClock;


    public JobPostingService(
            JobPostingRepository jobPostingRepository,
            CompanyRepository companyRepository,
            AdminAuthorizationService authorizationService,
            Clock catalogClock
    ) {
        this.jobPostingRepository = jobPostingRepository;
        this.companyRepository = companyRepository;
        this.authorizationService = authorizationService;
        this.catalogClock = catalogClock;
    }


    public JobPostingPageResponse search(
            String subject, Long companyId, String keyword, JobPostingStatus status, int page, int size
    ) {
        authorizationService.requireUser(subject);

        Long validatedCompanyId = companyId == null ? null : CatalogInput.id(companyId, "companyId");

        if (validatedCompanyId != null && !companyRepository.existsById(validatedCompanyId)) {
            throw new CompanyNotFoundException();
        }

        String pattern = CatalogInput.pattern(keyword);
        PageRequest pageable = CatalogInput.page(page, size);
        LocalDateTime now = now();

        return JobPostingPageResponse.from(
                jobPostingRepository.search(
                        validatedCompanyId, pattern, status == null ? null : status.name(), now, pageable
                )
        );
    }


    public JobPostingPageResponse getByCompany(
            String subject, Long companyId, String keyword, JobPostingStatus status, int page, int size
    ) {
        return search(subject, CatalogInput.id(companyId, "companyId"), keyword, status, page, size);
    }


    public JobPostingResponse get(String subject, Long jobPostingId) {
        authorizationService.requireUser(subject);
        Long validatedJobPostingId = CatalogInput.id(jobPostingId, "jobPostingId");

        LocalDateTime now = now();

        return JobPostingResponse.of(findDetail(validatedJobPostingId), now);
    }


    @Transactional
    public JobPostingResponse create(String subject, CreateJobPostingRequest request) {
        authorizationService.requireAdmin(subject);

        Long companyId = CatalogInput.id(request.companyId(), "companyId");
        String title = CatalogInput.text(request.title(), "title", 200, true);
        String jobRole = CatalogInput.text(request.jobRole(), "jobRole", 100, true);
        EmploymentType employmentType = requireEmploymentType(request.employmentType());
        String location = CatalogInput.text(request.location(), "location", 200, false);
        String description = CatalogInput.text(request.description(), "description", 30000, true);
        String sourceUrl = CatalogInput.url(request.sourceUrl(), "sourceUrl");
        LocalDateTime opensAt = CatalogInput.utc(request.opensAt(), "opensAt");
        LocalDateTime closesAt = CatalogInput.utc(request.closesAt(), "closesAt");

        CatalogInput.period(opensAt, closesAt);

        Company company = findLockedCompany(companyId);
        LocalDateTime now = now();

        JobPosting jobPosting = jobPostingRepository.save(
                JobPosting.create(
                        company,
                        title,
                        jobRole,
                        employmentType,
                        location,
                        description,
                        sourceUrl,
                        opensAt,
                        closesAt,
                        request.resolvedManuallyClosed(),
                        now
                )
        );

        return JobPostingResponse.of(jobPosting, now);
    }


    @Transactional
    public JobPostingResponse update(String subject, Long jobPostingId, UpdateJobPostingRequest request) {
        authorizationService.requireAdmin(subject);

        Long validatedJobPostingId = CatalogInput.id(jobPostingId, "jobPostingId");

        String title = CatalogInput.text(request.title(), "title", 200, true);
        String jobRole = CatalogInput.text(request.jobRole(), "jobRole", 100, true);
        EmploymentType employmentType = requireEmploymentType(request.employmentType());
        String location = CatalogInput.text(request.location(), "location", 200, false);
        String description = CatalogInput.text(request.description(), "description", 30000, true);
        String sourceUrl = CatalogInput.url(request.sourceUrl(), "sourceUrl");
        LocalDateTime opensAt = CatalogInput.utc(request.opensAt(), "opensAt");
        LocalDateTime closesAt = CatalogInput.utc(request.closesAt(), "closesAt");

        if (request.manuallyClosed() == null) {
            throw CatalogException.invalid("manuallyClosed", "필수 값입니다.");
        }

        CatalogInput.period(opensAt, closesAt);

        Long companyId = jobPostingRepository.findCompanyId(validatedJobPostingId)
                .orElseThrow(JobPostingNotFoundException::new);

        findLockedCompany(companyId);

        JobPosting jobPosting = jobPostingRepository.findDetailForUpdate(validatedJobPostingId)
                .orElseThrow(JobPostingNotFoundException::new);

        LocalDateTime now = now();

        jobPosting.update(
                title,
                jobRole,
                employmentType,
                location,
                description,
                sourceUrl,
                opensAt,
                closesAt,
                request.manuallyClosed(),
                now
        );

        return JobPostingResponse.of(jobPosting, now);
    }


    @Transactional
    public void delete(String subject, Long jobPostingId) {
        authorizationService.requireAdmin(subject);

        Long validatedJobPostingId = CatalogInput.id(jobPostingId, "jobPostingId");

        Long companyId = jobPostingRepository.findCompanyId(validatedJobPostingId)
                .orElseThrow(JobPostingNotFoundException::new);

        findLockedCompany(companyId);

        JobPosting jobPosting = jobPostingRepository.findDetailForUpdate(validatedJobPostingId)
                .orElseThrow(JobPostingNotFoundException::new);

        jobPostingRepository.delete(jobPosting);
    }


    private JobPosting findDetail(Long jobPostingId) {
        return jobPostingRepository.findDetail(jobPostingId).orElseThrow(JobPostingNotFoundException::new);
    }


    private EmploymentType requireEmploymentType(EmploymentType employmentType) {
        if (employmentType == null) {
            throw CatalogException.invalid("employmentType", "필수 값입니다.");
        }

        return employmentType;
    }


    private Company findLockedCompany(Long companyId) {
        return companyRepository.findLockedById(companyId).orElseThrow(CompanyNotFoundException::new);
    }


    private LocalDateTime now() {
        return LocalDateTime.now(catalogClock).truncatedTo(ChronoUnit.MICROS);
    }
}
