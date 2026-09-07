package com.interviewai.jobposting.controller;

import com.interviewai.jobposting.dto.CreateJobPostingRequest;
import com.interviewai.jobposting.dto.JobPostingResponse;
import com.interviewai.jobposting.dto.UpdateJobPostingRequest;
import com.interviewai.jobposting.service.JobPostingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/admin/job-postings")
public class AdminJobPostingController {

    private final JobPostingService jobPostingService;


    public AdminJobPostingController(JobPostingService jobPostingService) {
        this.jobPostingService = jobPostingService;
    }


    @PostMapping
    public ResponseEntity<JobPostingResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateJobPostingRequest request
    ) {
        JobPostingResponse response = jobPostingService.create(jwt.getSubject(), request);

        return ResponseEntity
                .created(URI.create("/api/job-postings/" + response.id()))
                .body(response);
    }


    @PutMapping("/{jobPostingId}")
    public JobPostingResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobPostingId,
            @Valid @RequestBody UpdateJobPostingRequest request
    ) {
        return jobPostingService.update(jwt.getSubject(), jobPostingId, request);
    }


    @DeleteMapping("/{jobPostingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long jobPostingId) {
        jobPostingService.delete(jwt.getSubject(), jobPostingId);
    }
}
