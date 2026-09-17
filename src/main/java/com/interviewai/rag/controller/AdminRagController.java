package com.interviewai.rag.controller;

import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.dto.AdminRagResponse;
import com.interviewai.rag.index.RagIndexJobStatus;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.service.AdminRagReindexService;
import com.interviewai.rag.service.AdminRagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/rag")
public class AdminRagController {

    private final AdminRagService adminRagService;
    private final AdminRagReindexService reindexService;


    @GetMapping("/sources")
    public AdminRagResponse.Page<AdminRagResponse.Source> sources(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) RagSourceType sourceType,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminRagService.sources(jwt.getSubject(), sourceType, sourceId, page, size);
    }


    @GetMapping("/sources/{sourceType}/{sourceId}")
    public AdminRagResponse.Source source(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable RagSourceType sourceType,
            @PathVariable Long sourceId
    ) {
        return adminRagService.source(jwt.getSubject(), sourceType, sourceId);
    }


    @GetMapping("/jobs")
    public AdminRagResponse.Page<AdminRagResponse.Job> jobs(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) RagSourceType sourceType,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) RagIndexJobStatus status,
            @RequestParam(required = false) RagIndexOperation operation,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminRagService.jobs(jwt.getSubject(), sourceType, sourceId, status, operation, page, size);
    }


    @GetMapping("/jobs/{jobId}")
    public AdminRagResponse.Job job(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId
    ) {
        return adminRagService.job(jwt.getSubject(), jobId);
    }


    @PostMapping("/jobs/{jobId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AdminRagResponse.Job retry(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId
    ) {
        return adminRagService.retry(jwt.getSubject(), jobId);
    }


    @PostMapping("/sources/{sourceType}/{sourceId}/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AdminRagResponse.Job reindex(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable RagSourceType sourceType,
            @PathVariable Long sourceId
    ) {
        return reindexService.reindex(jwt.getSubject(), sourceType, sourceId);
    }
}
