package com.interviewai.company.service;

import com.interviewai.company.dto.CompanyPageResponse;
import com.interviewai.company.dto.CompanyResponse;
import com.interviewai.company.dto.CreateCompanyRequest;
import com.interviewai.company.dto.UpdateCompanyRequest;
import com.interviewai.company.entity.Company;
import com.interviewai.company.exception.CompanyHasJobPostingsException;
import com.interviewai.company.exception.CompanyNotFoundException;
import com.interviewai.company.repository.CompanyFavoriteRepository;
import com.interviewai.company.repository.CompanyRepository;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.global.validation.CatalogInput;
import com.interviewai.jobposting.repository.JobPostingRepository;
import com.interviewai.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@Transactional(readOnly = true)
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyFavoriteRepository favoriteRepository;
    private final JobPostingRepository jobPostingRepository;
    private final AdminAuthorizationService authorizationService;
    private final Clock catalogClock;


    public CompanyService(
            CompanyRepository companyRepository,
            CompanyFavoriteRepository favoriteRepository,
            JobPostingRepository jobPostingRepository,
            AdminAuthorizationService authorizationService,
            Clock catalogClock
    ) {
        this.companyRepository = companyRepository;
        this.favoriteRepository = favoriteRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.authorizationService = authorizationService;
        this.catalogClock = catalogClock;
    }


    public CompanyPageResponse search(String subject, String keyword, int page, int size) {
        User user = authorizationService.requireUser(subject);
        String pattern = CatalogInput.pattern(keyword);
        PageRequest pageable = CatalogInput.page(page, size);

        return CompanyPageResponse.from(companyRepository.search(user.getId(), pattern, pageable));
    }


    public CompanyResponse get(String subject, Long companyId) {
        User user = authorizationService.requireUser(subject);
        Long validatedCompanyId = CatalogInput.id(companyId, "companyId");

        Company company = findCompany(validatedCompanyId);
        boolean favorite = favoriteRepository.existsByUserIdAndCompanyId(user.getId(), validatedCompanyId);

        return CompanyResponse.of(company, favorite);
    }


    public CompanyPageResponse getFavorites(String subject, int page, int size) {
        User user = authorizationService.requireUser(subject);

        PageRequest pageable = CatalogInput.page(page, size).withSort(Sort.unsorted());

        return CompanyPageResponse.from(favoriteRepository.findSummaries(user.getId(), pageable));
    }


    @Transactional
    public void addFavorite(String subject, Long companyId) {
        User user = authorizationService.requireUser(subject);
        Long validatedCompanyId = CatalogInput.id(companyId, "companyId");

        findLockedCompany(validatedCompanyId);

        favoriteRepository.add(user.getId(), validatedCompanyId, now());
    }


    @Transactional
    public void removeFavorite(String subject, Long companyId) {
        User user = authorizationService.requireUser(subject);
        Long validatedCompanyId = CatalogInput.id(companyId, "companyId");

        favoriteRepository.remove(user.getId(), validatedCompanyId);
    }


    @Transactional
    public CompanyResponse create(String subject, CreateCompanyRequest request) {
        authorizationService.requireAdmin(subject);

        String name = CatalogInput.text(request.name(), "name", 100, true);
        String industry = CatalogInput.text(request.industry(), "industry", 100, false);
        String description = CatalogInput.text(request.description(), "description", 20000, true);
        String websiteUrl = CatalogInput.url(request.websiteUrl(), "websiteUrl");
        String location = CatalogInput.text(request.location(), "location", 200, false);

        Company company = companyRepository.save(
                Company.create(name, industry, description, websiteUrl, location, now())
        );

        return CompanyResponse.of(company, false);
    }


    @Transactional
    public CompanyResponse update(String subject, Long companyId, UpdateCompanyRequest request) {
        User admin = authorizationService.requireAdmin(subject);
        Long validatedCompanyId = CatalogInput.id(companyId, "companyId");

        String name = CatalogInput.text(request.name(), "name", 100, true);
        String industry = CatalogInput.text(request.industry(), "industry", 100, false);
        String description = CatalogInput.text(request.description(), "description", 20000, true);
        String websiteUrl = CatalogInput.url(request.websiteUrl(), "websiteUrl");
        String location = CatalogInput.text(request.location(), "location", 200, false);

        Company company = findLockedCompany(validatedCompanyId);

        company.update(name, industry, description, websiteUrl, location, now());

        boolean favorite = favoriteRepository.existsByUserIdAndCompanyId(admin.getId(), validatedCompanyId);

        return CompanyResponse.of(company, favorite);
    }


    @Transactional
    public void delete(String subject, Long companyId) {
        authorizationService.requireAdmin(subject);
        Long validatedCompanyId = CatalogInput.id(companyId, "companyId");

        Company company = findLockedCompany(validatedCompanyId);

        if (jobPostingRepository.existsByCompanyId(validatedCompanyId)) {
            throw new CompanyHasJobPostingsException();
        }

        companyRepository.delete(company);
    }


    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId).orElseThrow(CompanyNotFoundException::new);
    }


    private Company findLockedCompany(Long companyId) {
        return companyRepository.findLockedById(companyId).orElseThrow(CompanyNotFoundException::new);
    }


    private LocalDateTime now() {
        return LocalDateTime.now(catalogClock).truncatedTo(ChronoUnit.MICROS);
    }
}
