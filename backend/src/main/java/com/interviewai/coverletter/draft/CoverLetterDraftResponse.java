package com.interviewai.coverletter.draft;

import java.time.LocalDateTime;
import java.util.List;

public record CoverLetterDraftResponse(
        Long id,
        Long coverLetterId,
        Long sourceDraftId,
        Long jobPostingId,
        Long resumeId,
        Integer baseVersionNumber,
        String inputHash,
        CoverLetterDraftStatus status,
        String instruction,
        String generatedTitle,
        String generatedContent,
        String changeSummary,
        List<String> warnings,
        String failureCode,
        Integer attemptCount,
        Integer appliedVersionNumber,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime generatedAt,
        LocalDateTime appliedAt
) {

    public static CoverLetterDraftResponse of(CoverLetterDraft draft) {
        return new CoverLetterDraftResponse(
                draft.getId(),
                draft.getCoverLetter().getId(),
                draft.getSourceDraft() == null ? null : draft.getSourceDraft().getId(),
                draft.getJobPostingId(),
                draft.getResumeId(),
                draft.getBaseVersionNumber(),
                draft.getInputHash(),
                draft.getStatus(),
                draft.getInstruction(),
                draft.getGeneratedTitle(),
                draft.getGeneratedContent(),
                draft.getChangeSummary(),
                immutableWarnings(draft.getWarnings()),
                draft.getFailureCode(),
                draft.getAttemptCount(),
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
