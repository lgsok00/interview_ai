package com.interviewai.interview.controller;

import com.interviewai.interview.dto.CreateInterviewSessionRequest;
import com.interviewai.interview.dto.InterviewQuestionResponse;
import com.interviewai.interview.dto.InterviewSessionPageResponse;
import com.interviewai.interview.dto.InterviewSessionResponse;
import com.interviewai.interview.service.InterviewSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interview-sessions")
public class InterviewSessionController {

    private final InterviewSessionService interviewSessionService;


    public InterviewSessionController(InterviewSessionService interviewSessionService) {
        this.interviewSessionService = interviewSessionService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewSessionResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInterviewSessionRequest request
    ) {
        return interviewSessionService.create(jwt.getSubject(), request);
    }


    @GetMapping
    public InterviewSessionPageResponse getAll(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return interviewSessionService.getAll(jwt.getSubject(), page, size);
    }


    @GetMapping("/{sessionId}")
    public InterviewSessionResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId
    ) {
        return interviewSessionService.get(jwt.getSubject(), sessionId);
    }


    @GetMapping("/{sessionId}/questions")
    public List<InterviewQuestionResponse> getQuestions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId
    ) {
        return interviewSessionService.getQuestions(jwt.getSubject(), sessionId);
    }


    @PostMapping("/{sessionId}/start")
    public InterviewSessionResponse start(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId
    ) {
        return interviewSessionService.start(jwt.getSubject(), sessionId);
    }


    @PostMapping("/{sessionId}/complete")
    public InterviewSessionResponse complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId
    ) {
        return interviewSessionService.complete(jwt.getSubject(), sessionId);
    }


    @PostMapping("/{sessionId}/generation/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void retryGeneration(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId
    ) {
        interviewSessionService.retryGeneration(jwt.getSubject(), sessionId);
    }
}
