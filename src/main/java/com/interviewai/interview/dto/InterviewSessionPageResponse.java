package com.interviewai.interview.dto;

import com.interviewai.interview.entity.InterviewSession;
import org.springframework.data.domain.Page;

import java.util.List;

public record InterviewSessionPageResponse(
        List<InterviewSessionSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static InterviewSessionPageResponse from(Page<InterviewSession> sessions) {
        return new InterviewSessionPageResponse(
                sessions.getContent().stream()
                        .map(InterviewSessionSummaryResponse::from)
                        .toList(),
                sessions.getNumber(),
                sessions.getSize(),
                sessions.getTotalElements(),
                sessions.getTotalPages(),
                sessions.isFirst(),
                sessions.isLast()
        );
    }
}
