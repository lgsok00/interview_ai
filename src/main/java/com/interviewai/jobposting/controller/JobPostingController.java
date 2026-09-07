package com.interviewai.jobposting.controller;

import com.interviewai.jobposting.dto.JobPostingPageResponse;
import com.interviewai.jobposting.dto.JobPostingResponse;
import com.interviewai.jobposting.enums.JobPostingStatus;
import com.interviewai.jobposting.service.JobPostingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-postings")
public class JobPostingController {

    private final JobPostingService jobPostingService;


    public JobPostingController(JobPostingService jobPostingService) {
        this.jobPostingService = jobPostingService;
    }


    @GetMapping
    public JobPostingPageResponse search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) JobPostingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return jobPostingService.search(jwt.getSubject(), companyId, keyword, status, page, size);
    }


    @GetMapping("/{jobPostingId}")
    public JobPostingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long jobPostingId) {
        return jobPostingService.get(jwt.getSubject(), jobPostingId);
    }
}
