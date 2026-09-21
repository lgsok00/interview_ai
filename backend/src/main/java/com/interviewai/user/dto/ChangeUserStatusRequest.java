package com.interviewai.user.dto;

import com.interviewai.user.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeUserStatusRequest(
        @NotNull(message = "사용자 상태는 필수입니다.")
        UserStatus status
) {

}
