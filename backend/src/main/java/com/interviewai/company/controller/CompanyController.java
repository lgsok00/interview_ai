package com.interviewai.company.controller;

import com.interviewai.company.dto.CompanyPageResponse;
import com.interviewai.company.dto.CompanyResponse;
import com.interviewai.company.service.CompanyService;
import com.interviewai.jobposting.dto.JobPostingPageResponse;
import com.interviewai.jobposting.enums.JobPostingStatus;
import com.interviewai.jobposting.service.JobPostingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final JobPostingService jobPostingService;


    public CompanyController(CompanyService companyService, JobPostingService jobPostingService) {
        this.companyService = companyService;
        this.jobPostingService = jobPostingService;
    }


    @GetMapping
    public CompanyPageResponse search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return companyService.search(jwt.getSubject(), keyword, page, size);
    }


    @GetMapping("/favorites")
    public CompanyPageResponse getFavorites(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return companyService.getFavorites(jwt.getSubject(), page, size);
    }


    @GetMapping("/{companyId}")
    public CompanyResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        return companyService.get(jwt.getSubject(), companyId);
    }


    @GetMapping("/{companyId}/job-postings")
    public JobPostingPageResponse getJobPostings(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) JobPostingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return jobPostingService.getByCompany(jwt.getSubject(), companyId, keyword, status, page, size);
    }


    @PutMapping("/{companyId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addFavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        companyService.addFavorite(jwt.getSubject(), companyId);
    }


    @DeleteMapping("/{companyId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        companyService.removeFavorite(jwt.getSubject(), companyId);
    }
}
