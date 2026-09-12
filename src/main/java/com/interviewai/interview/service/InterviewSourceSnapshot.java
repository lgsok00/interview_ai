package com.interviewai.interview.service;

public record InterviewSourceSnapshot(
        JobPostingSnapshot jobPosting,
        PersonalDocumentSnapshot coverLetter,
        PersonalDocumentSnapshot resume
) {

    public record JobPostingSnapshot(
            Long id,
            String companyName,
            String title,
            String jobRole,
            String content
    ) {
    }

    public record PersonalDocumentSnapshot(
            Long id,
            String title,
            String content
    ) {
    }
}
