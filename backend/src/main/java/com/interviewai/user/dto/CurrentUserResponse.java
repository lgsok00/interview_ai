package com.interviewai.user.dto;

import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;

public record CurrentUserResponse(
        Long id,
        String email,
        String nickname,
        AuthProvider provider,
        UserRole role
) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProvider(),
                user.getRole()
        );
    }
}
