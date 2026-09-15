package com.interviewai.interview.dto;

import com.interviewai.interview.entity.InterviewAnswer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record InterviewAnswerResponse(
        Long id,
        Long questionId,
        String content,
        Long followUpQuestionId,
        OffsetDateTime createdAt
) {

    public static InterviewAnswerResponse from(InterviewAnswer answer, Long followUpQuestionId) {
        return new InterviewAnswerResponse(
                answer.getId(),
                answer.getQuestion().getId(),
                answer.getContent(),
                followUpQuestionId,
                answer.getCreatedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
