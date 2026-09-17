package com.interviewai.user.dto;

import com.interviewai.user.entity.User;
import org.springframework.data.domain.Page;

import java.util.List;

public record AdminUserPageResponse(
        List<AdminUserResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static AdminUserPageResponse from(Page<User> result) {
        List<AdminUserResponse> items = result.getContent()
                .stream()
                .map(AdminUserResponse::from)
                .toList();

        return new AdminUserPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
