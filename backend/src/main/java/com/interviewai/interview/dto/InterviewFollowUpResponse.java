package com.interviewai.interview.dto;

import com.interviewai.interview.entity.InterviewQuestion;

public record InterviewFollowUpResponse(
        Long parentQuestionId,
        InterviewQuestionResponse question
) {

    public static InterviewFollowUpResponse from(InterviewQuestion question) {
        return new InterviewFollowUpResponse(question.getParentQuestionId(), InterviewQuestionResponse.from(question));
    }
}
