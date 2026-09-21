package com.interviewai.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitInterviewAnswerRequest(
        @NotBlank(message = "답변은 필수입니다.")
        @Size(max = 10000, message = "답변은 10000자 이하여야 합니다.")
        String content
) {

    public SubmitInterviewAnswerRequest {
        content = content == null ? null : content.strip();
    }
}
