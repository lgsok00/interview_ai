package com.interviewai.interview.controller;

import com.interviewai.interview.dto.InterviewGrowthAnalysisResponse;
import com.interviewai.interview.dto.InterviewResultResponse;
import com.interviewai.interview.service.InterviewAnalysisService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class InterviewAnalysisController {

    private final InterviewAnalysisService analysisService;


    public InterviewAnalysisController(InterviewAnalysisService analysisService) {
        this.analysisService = analysisService;
    }


    @GetMapping("/interview-sessions/{sessionId}/result")
    public InterviewResultResponse getResult(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId
    ) {
        return analysisService.getResult(jwt.getSubject(), sessionId);
    }


    @GetMapping("/interview-growth-analysis")
    public InterviewGrowthAnalysisResponse getGrowthAnalysis(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String jobRole,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long jobPostingId
    ) {
        return analysisService.getGrowthAnalysis(jwt.getSubject(), from, to, jobRole, companyId, jobPostingId);
    }
}
