package com.interviewai.user.dto;

import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.enums.UserStatus;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String email,
        String nickname,
        AuthProvider provider,
        UserRole role,
        UserStatus status,
        LocalDateTime suspendedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProvider(),
                user.getRole(),
                user.getStatus(),
                user.getSuspendedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
