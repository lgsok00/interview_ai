package com.interviewai.coverletter.draft;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCoverLetterDraftRequest(
        @NotNull(message = "채용공고 ID는 필수입니다.")
        @Positive(message = "채용공고 ID는 1 이상이어야 합니다.")
        Long jobPostingId,

        @Positive(message = "이력서 ID는 1 이상이어야 합니다.")
        Long resumeId,

        @Size(min = 1, max = 1000, message = "추가 지시는 1자 이상 1000자 이하여야 합니다.")
        String instruction
) {

    public CreateCoverLetterDraftRequest {
        if (instruction != null) {
            instruction = instruction.strip();
        }
    }
}
