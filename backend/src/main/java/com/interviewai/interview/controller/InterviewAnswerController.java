package com.interviewai.interview.controller;

import com.interviewai.interview.dto.InterviewAnswerResponse;
import com.interviewai.interview.dto.InterviewFollowUpResponse;
import com.interviewai.interview.dto.SubmitInterviewAnswerRequest;
import com.interviewai.interview.service.InterviewAnswerService;
import com.interviewai.interview.service.InterviewFollowUpService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interview-sessions/{sessionId}")
public class InterviewAnswerController {

    private final InterviewAnswerService answerService;
    private final InterviewFollowUpService followUpService;


    public InterviewAnswerController(
            InterviewAnswerService answerService,
            InterviewFollowUpService followUpService
    ) {
        this.answerService = answerService;
        this.followUpService = followUpService;
    }


    @PutMapping("/questions/{questionId}/answer")
    public InterviewAnswerResponse submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId,
            @PathVariable long questionId,
            @Valid @RequestBody SubmitInterviewAnswerRequest request
    ) {
        return answerService.submit(jwt.getSubject(), sessionId, questionId, request);
    }


    @GetMapping("/answers")
    public List<InterviewAnswerResponse> getAll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId
    ) {
        return answerService.getAll(jwt.getSubject(), sessionId);
    }


    @PostMapping("/questions/{questionId}/follow-up")
    public InterviewFollowUpResponse generateFollowUp(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId,
            @PathVariable long questionId
    ) {
        return followUpService.generate(jwt.getSubject(), sessionId, questionId);
    }
}
