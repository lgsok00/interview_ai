package com.interviewai.coverletter.draft;

import java.time.LocalDateTime;
import java.util.List;

public record CoverLetterDraftSummaryResponse(
        Long id,
        Long sourceDraftId,
        Long jobPostingId,
        Long resumeId,
        Integer baseVersionNumber,
        CoverLetterDraftStatus status,
        String generatedTitle,
        String changeSummary,
        List<String> warnings,
        String failureCode,
        Integer appliedVersionNumber,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime generatedAt,
        LocalDateTime appliedAt
) {

    public static CoverLetterDraftSummaryResponse of(CoverLetterDraft draft) {
        return new CoverLetterDraftSummaryResponse(
                draft.getId(),
                draft.getSourceDraft() == null ? null : draft.getSourceDraft().getId(),
                draft.getJobPostingId(),
                draft.getResumeId(),
                draft.getBaseVersionNumber(),
                draft.getStatus(),
                draft.getGeneratedTitle(),
                draft.getChangeSummary(),
                immutableWarnings(draft.getWarnings()),
                draft.getFailureCode(),
                draft.getAppliedVersionNumber(),
                draft.getCreatedAt(),
                draft.getUpdatedAt(),
                draft.getGeneratedAt(),
                draft.getAppliedAt()
        );
    }

    
    private static List<String> immutableWarnings(List<String> warnings) {
        return warnings == null ? List.of() : List.copyOf(warnings);
    }
}
