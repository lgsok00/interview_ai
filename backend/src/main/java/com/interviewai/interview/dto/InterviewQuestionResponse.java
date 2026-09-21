package com.interviewai.interview.dto;

import com.interviewai.interview.entity.InterviewQuestion;
import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.QuestionGenerationSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record InterviewQuestionResponse(
        Long id,
        int sequenceNumber,
        InterviewQuestionType questionType,
        QuestionGenerationSource generationSource,
        String content,
        OffsetDateTime createdAt
) {

    public static InterviewQuestionResponse from(InterviewQuestion question) {
        return new InterviewQuestionResponse(
                question.getId(),
                question.getSequenceNumber(),
                question.getQuestionType(),
                question.getGenerationSource(),
                question.getContent(),
                question.getCreatedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
