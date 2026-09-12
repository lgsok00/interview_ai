package com.interviewai.interview.controller;

import com.interviewai.interview.dto.CreateInterviewSessionRequest;
import com.interviewai.interview.dto.InterviewSessionResponse;
import com.interviewai.interview.service.InterviewSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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
}
