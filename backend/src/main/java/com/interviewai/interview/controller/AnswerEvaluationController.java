package com.interviewai.interview.controller;

import com.interviewai.interview.dto.AnswerEvaluationResponse;
import com.interviewai.interview.service.AnswerEvaluationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-sessions/{sessionId}/answers/{answerId}/evaluation")
public class AnswerEvaluationController {

    private final AnswerEvaluationService evaluationService;


    public AnswerEvaluationController(AnswerEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnswerEvaluationResponse request(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId,
            @PathVariable long answerId
    ) {
        return evaluationService.request(jwt.getSubject(), sessionId, answerId);
    }


    @GetMapping
    public AnswerEvaluationResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId,
            @PathVariable long answerId
    ) {
        return evaluationService.get(jwt.getSubject(), sessionId, answerId);
    }


    @PostMapping("/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnswerEvaluationResponse retry(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId,
            @PathVariable long answerId
    ) {
        return evaluationService.retry(jwt.getSubject(), sessionId, answerId);
    }
}
