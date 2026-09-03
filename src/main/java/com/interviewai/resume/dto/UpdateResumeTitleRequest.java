package com.interviewai.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateResumeTitleRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        String title
) {

    public UpdateResumeTitleRequest {
        if (title != null) {
            title = title.trim();
        }
    }
}
