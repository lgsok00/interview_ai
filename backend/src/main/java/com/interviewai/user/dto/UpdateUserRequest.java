package com.interviewai.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(
                min = 2,
                max = 50,
                message = "닉네임은 2자 이상 50자 이하여야 합니다."
        )
        String nickname
) {

    public UpdateUserRequest {
        if (nickname != null) {
            nickname = nickname.trim();
        }
    }
}
