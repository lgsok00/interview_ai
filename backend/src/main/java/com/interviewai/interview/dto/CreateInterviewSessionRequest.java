package com.interviewai.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateInterviewSessionRequest(

        @NotNull(message = "채용공고 ID는 필수입니다.")
        @Positive(message = "채용공고 ID는 1 이상이어야 합니다.")
        Long jobPostingId
) {
}
