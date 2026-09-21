package com.interviewai.coverletter.draft;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyCoverLetterDraftRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "본문은 필수입니다.")
        @Size(max = 20000, message = "본문은 20,000자 이하여야 합니다.")
        String content
) {

    public ApplyCoverLetterDraftRequest {
        if (title != null) {
            title = title.strip();
        }

        if (content != null) {
            content = content.strip();
        }
    }
}
