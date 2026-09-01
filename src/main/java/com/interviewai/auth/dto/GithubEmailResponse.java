package com.interviewai.auth.dto;

public record GithubEmailResponse(
        String email,
        boolean primary,
        boolean verified
) {
}
