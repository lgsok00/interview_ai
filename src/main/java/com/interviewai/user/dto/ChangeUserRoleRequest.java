package com.interviewai.user.dto;

import com.interviewai.user.enums.UserRole;
import jakarta.validation.constraints.NotNull;

public record ChangeUserRoleRequest(
        @NotNull(message = "role은 필수입니다.")
        UserRole role
) {

}
