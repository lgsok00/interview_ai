package com.interviewai.coverletter.draft;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class CoverLetterDraftExecutionService {

    private final CoverLetterDraftRepository draftRepository;
    private final JdbcTemplate jdbc;


    public CoverLetterDraftExecutionService(CoverLetterDraftRepository draftRepository, JdbcTemplate jdbc) {
        this.draftRepository = draftRepository;
        this.jdbc = jdbc;
    }


    public boolean recoverOneExhaustedLease() {
        CoverLetterDraft draft = draftRepository.findNextExhaustedExpiredForUpdate().orElse(null);

        if (draft == null) {
            return false;
        }

        draft.failExpired("DRAFT_LEASE_EXPIRED", databaseNow());

        return true;
    }


    public Optional<Claim> claimNext() {
        CoverLetterDraft draft = draftRepository.findNextClaimableForUpdate().orElse(null);

        if (draft == null) {
            return Optional.empty();
        }

        LocalDateTime now = databaseNow();
        CoverLetterDraft.Attempt attempt = draft.claim(UUID.randomUUID(), now);

        return Optional.of(new Claim(
                draft.getId(),
                attempt.id(),
                draft.getModel(),
                draft.getPromptTemplateVersion(),
                toInput(draft)
        ));
    }


    public boolean complete(Claim claim, CoverLetterDraft.GenerateDraft generated) {
        CoverLetterDraft draft = draftRepository.findByIdForUpdate(claim.draftId()).orElse(null);

        LocalDateTime now = databaseNow();

        if (isInactive(draft, claim, now)) {
            return false;
        }

        draft.complete(claim.attemptId(), generated, now);

        return true;
    }


    public boolean fail(Claim claim, String failureCode, boolean retryable) {
        validateFailureCode(failureCode);

        CoverLetterDraft draft = draftRepository.findByIdForUpdate(claim.draftId()).orElse(null);

        LocalDateTime now = databaseNow();

        if (isInactive(draft, claim, now)) {
            return false;
        }

        if (retryable && draft.getAttemptCount() < CoverLetterDraft.MAX_ATTEMPTS) {
            draft.retry(
                    claim.attemptId(),
                    now.plus(CoverLetterDraftPolicy.retryDelay(draft.getAttemptCount())),
                    now
            );

        } else {
            draft.fail(claim.attemptId(), failureCode, now);
        }

        return true;
    }


    private boolean isInactive(CoverLetterDraft draft, Claim claim, LocalDateTime now) {
        return draft == null
                || draft.getStatus() != CoverLetterDraftStatus.RUNNING
                || !Objects.equals(draft.getAttemptId(), claim.attemptId)
                || draft.getLeaseExpiresAt() == null
                || !draft.getLeaseExpiresAt().isAfter(now);
    }


    private CoverLetterDraft.InputSnapshot toInput(CoverLetterDraft draft) {
        return new CoverLetterDraft.InputSnapshot(
                draft.getJobPostingId(),
                draft.getResumeId(),
                draft.getBaseVersionNumber(),
                draft.getCoverLetterTitle(),
                draft.getCoverLetterContent(),

                draft.getCompanyId(),
                draft.getCompanyName(),
                draft.getCompanyIndustry(),
                draft.getCompanyDescription(),
                draft.getCompanyWebsiteUrl(),
                draft.getCompanyLocation(),

                draft.getJobPostingTitle(),
                draft.getJobRole(),
                draft.getEmploymentType(),
                draft.getJobPostingLocation(),
                draft.getJobPostingDescription(),
                draft.getJobPostingSourceUrl(),
                draft.getJobPostingOpensAt(),
                draft.getJobPostingClosesAt(),

                draft.getResumeTitle(),
                draft.getResumeContent(),
                draft.getInstruction()
        );
    }


    private void validateFailureCode(String failureCode) {
        if (failureCode == null || !failureCode.matches("[A-Z][A-Z0-9_]{0,49}")) {
            throw new IllegalArgumentException("초안 생성 실패 코드 형식이 올바르지 않습니다.");
        }
    }


    private LocalDateTime databaseNow() {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT UTC_TIMESTAMP(6)",
                        LocalDateTime.class
                ),
                "DB 현재 시각은 필수입니다."
        );
    }


    public record Claim(
            long draftId,
            String attemptId,
            String model,
            String promptTemplateVersion,
            CoverLetterDraft.InputSnapshot input
    ) {

    }
}
