package com.interviewai.coverletter.draft;

import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@Table(name = "cover_letter_drafts")
public class CoverLetterDraft {

    public static final int MAX_ATTEMPTS = 3;
    public static final long LEASE_SECONDS = 120;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_id", nullable = false, updatable = false)
    private CoverLetter coverLetter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_draft_id", updatable = false)
    private CoverLetterDraft sourceDraft;

    @Column(name = "job_posting_id", nullable = false, updatable = false)
    private Long jobPostingId;

    @Column(name = "resume_id", updatable = false)
    private Long resumeId;

    @Column(name = "base_version_number", nullable = false, updatable = false)
    private Integer baseVersionNumber;

    @Column(name = "cover_letter_title", nullable = false, length = 100, updatable = false)
    private String coverLetterTitle;

    @Column(name = "cover_letter_content", nullable = false, columnDefinition = "MEDIUMTEXT", updatable = false)
    private String coverLetterContent;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "company_name", nullable = false, length = 100, updatable = false)
    private String companyName;

    @Column(name = "company_industry", length = 100, updatable = false)
    private String companyIndustry;

    @Column(name = "company_description", nullable = false, columnDefinition = "MEDIUMTEXT", updatable = false)
    private String companyDescription;

    @Column(name = "company_website_url", length = 2048, updatable = false)
    private String companyWebsiteUrl;

    @Column(name = "company_location", length = 200, updatable = false)
    private String companyLocation;

    @Column(name = "job_posting_title", nullable = false, length = 200, updatable = false)
    private String jobPostingTitle;

    @Column(name = "job_role", nullable = false, length = 100, updatable = false)
    private String jobRole;

    @Column(name = "employment_type", nullable = false, length = 20, updatable = false)
    private String employmentType;

    @Column(name = "job_posting_location", length = 200, updatable = false)
    private String jobPostingLocation;

    @Column(name = "job_posting_description", nullable = false, columnDefinition = "MEDIUMTEXT", updatable = false)
    private String jobPostingDescription;

    @Column(name = "job_posting_source_url", length = 2048, updatable = false)
    private String jobPostingSourceUrl;

    @Column(name = "job_posting_opens_at", updatable = false)
    private LocalDateTime jobPostingOpensAt;

    @Column(name = "job_posting_closes_at", updatable = false)
    private LocalDateTime jobPostingClosesAt;

    @Column(name = "resume_title", length = 100, updatable = false)
    private String resumeTitle;

    @Column(name = "resume_content", columnDefinition = "MEDIUMTEXT", updatable = false)
    private String resumeContent;

    @Column(length = 1000, updatable = false)
    private String instruction;

    @Column(name = "input_hash", nullable = false, length = 64, updatable = false)
    private String inputHash;

    @Column(name = "prompt_template_version", nullable = false, length = 50, updatable = false)
    private String promptTemplateVersion;

    @Column(nullable = false, length = 100, updatable = false)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoverLetterDraftStatus status;

    @Column(name = "generated_title", length = 100)
    private String generatedTitle;

    @Column(name = "generated_content", columnDefinition = "MEDIUMTEXT")
    private String generatedContent;

    @Column(name = "change_summary", length = 1000)
    private String changeSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings_json", columnDefinition = "JSON")
    private List<String> warnings;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "attempt_id", length = 36)
    private String attemptId;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "applied_version_number")
    private Integer appliedVersionNumber;

    @Column(name = "applied_title", length = 100)
    private String appliedTitle;

    @Column(name = "applied_content", columnDefinition = "MEDIUMTEXT")
    private String appliedContent;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    protected CoverLetterDraft() {

    }


    private CoverLetterDraft(
            User user,
            CoverLetter coverLetter,
            CoverLetterDraft sourceDraft,
            InputSnapshot input,
            String inputHash,
            String promptTemplateVersion,
            String model,
            LocalDateTime now
    ) {
        this.user = Objects.requireNonNull(user, "user는 필수입니다.");
        this.coverLetter = Objects.requireNonNull(coverLetter, "coverLetter는 필수입니다.");
        this.sourceDraft = sourceDraft;

        Objects.requireNonNull(input, "input은 필수입니다.");

        this.jobPostingId = requirePositive(input.jobPostingId(), "jobPostingId");
        this.resumeId = optionalPositive(input.resumeId());
        this.baseVersionNumber = requirePositive(input.baseVersionNumber(), "baseVersionNumber");

        this.coverLetterTitle = requireText(input.coverLetterTitle(), "coverLetterTitle", 100);
        this.coverLetterContent = requireText(input.coverLetterContent(), "coverLetterContent", 20_000);

        this.companyId = requirePositive(input.companyId(), "companyId");
        this.companyName = requireText(input.companyName(), "companyName", 100);
        this.companyIndustry = optionalText(input.companyIndustry(), 100);
        this.companyDescription = requireText(input.companyDescription(), "companyDescription");
        this.companyWebsiteUrl = optionalText(input.companyWebsiteUrl(), 2048);
        this.companyLocation = optionalText(input.companyLocation(), 200);

        this.jobPostingTitle = requireText(input.jobPostingTitle(), "jobPostingTitle", 200);
        this.jobRole = requireText(input.jobRole(), "jobRole", 100);
        this.employmentType = requireText(input.employmentType(), "employmentType", 20);
        this.jobPostingLocation = optionalText(input.jobPostingLocation(), 200);
        this.jobPostingDescription = requireText(input.jobPostingDescription(), "jobPostingDescription");
        this.jobPostingSourceUrl = optionalText(input.jobPostingSourceUrl(), 2048);
        this.jobPostingOpensAt = input.jobPostingOpensAt();
        this.jobPostingClosesAt = input.jobPostingClosesAt();

        this.resumeTitle = optionalText(input.resumeTitle(), 100);
        this.resumeContent = optionalText(input.resumeContent());
        this.instruction = optionalText(input.instruction(), 1000);

        validateResumeSnapshot();

        this.inputHash = requireHash(inputHash);
        this.promptTemplateVersion = requireText(promptTemplateVersion, "promptTemplateVersion", 50);
        this.model = requireText(model, "model", 100);

        LocalDateTime created = Objects.requireNonNull(now, "now는 필수입니다.");

        this.status = CoverLetterDraftStatus.PENDING;
        this.attemptCount = 0;
        this.availableAt = created;
        this.createdAt = created;
        this.updatedAt = created;
    }


    public static CoverLetterDraft create(
            User user,
            CoverLetter coverLetter,
            CoverLetterDraft sourceDraft,
            InputSnapshot input,
            String inputHash,
            String promptTemplateVersion,
            String model,
            LocalDateTime now
    ) {
        return new CoverLetterDraft(
                user,
                coverLetter,
                sourceDraft,
                input,
                inputHash,
                promptTemplateVersion,
                model,
                now
        );
    }


    private static List<String> validateWarnings(List<String> values) {
        Objects.requireNonNull(values, "warnings는 필수입니다.");

        return values.stream()
                .map(value -> requireText(value, "warning", 1000))
                .toList();
    }


    private static String requireHash(String value) {
        String normalized = requireText(value, "inputHash", 64);

        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("inputHash는 소문자 SHA-256 형식이어야 합니다.");
        }

        return normalized;
    }


    private static String requireFailureCode(String value) {
        String normalized = requireText(value, "failureCode", 50);

        if (!normalized.matches("[A-Z][A-Z0-9_]{0,49}")) {
            throw new IllegalArgumentException("실패 코드 형식이 올바르지 않습니다.");
        }

        return normalized;
    }


    private static int requirePositive(Integer value, String field) {
        Objects.requireNonNull(value, field + "는 필수입니다.");

        if (value < 1) {
            throw new IllegalArgumentException(field + "는 1 이상이어야 합니다.");
        }

        return value;
    }


    private static long requirePositive(Long value, String field) {
        Objects.requireNonNull(value, field + "는 필수입니다.");

        if (value < 1) {
            throw new IllegalArgumentException(field + "는 1 이상이어야 합니다.");
        }

        return value;
    }


    private static Long optionalPositive(Long value) {
        if (value != null && value < 1) {
            throw new IllegalArgumentException("resumeId는 1 이상이어야 합니다.");
        }

        return value;
    }


    private static String requireText(String value, String field) {
        String normalized = normalizeRequired(value);

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + "는 비어 있을 수 없습니다.");
        }

        return normalized;
    }


    private static String requireText(String value, String field, int maximumLength) {
        String normalized = requireText(value, field);

        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + "는 " + maximumLength + "자 이하여야 합니다.");
        }

        return normalized;
    }


    private static String normalizeRequired(String value) {
        return Objects.requireNonNull(value, "값은 필수입니다.").strip();
    }


    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.strip();

        return normalized.isEmpty() ? null : normalized;
    }


    private static String optionalText(String value, int maximumLength) {
        String normalized = optionalText(value);

        if (normalized != null && normalized.length() > maximumLength) {
            throw new IllegalArgumentException("선택 문자열은 " + maximumLength + "자 이하여야 합니다.");
        }

        return normalized;
    }


    public boolean canBeClaimed(LocalDateTime now) {
        Objects.requireNonNull(now, "now는 필수입니다.");

        boolean pendingAndAvailable = status == CoverLetterDraftStatus.PENDING && !availableAt.isAfter(now);
        boolean expired = status == CoverLetterDraftStatus.RUNNING
                && leaseExpiresAt != null
                && !leaseExpiresAt.isAfter(now);

        return attemptCount < MAX_ATTEMPTS && (pendingAndAvailable || expired);
    }


    public Attempt claim(UUID nextAttemptId, LocalDateTime now) {
        Objects.requireNonNull(nextAttemptId, "nextAttemptId는 필수입니다.");
        Objects.requireNonNull(now, "now는 필수입니다.");

        if (!canBeClaimed(now)) {
            throw new IllegalStateException("초안을 선점할 수 없는 상태입니다.");
        }

        this.attemptCount += 1;
        this.status = CoverLetterDraftStatus.RUNNING;
        this.attemptId = nextAttemptId.toString();
        this.leaseExpiresAt = now.plusSeconds(LEASE_SECONDS);
        this.failureCode = null;
        this.updatedAt = now;

        return new Attempt(this.attemptCount, this.attemptId);
    }


    public void retry(String expectedAttemptId, LocalDateTime nextAvailableAt, LocalDateTime now) {
        requireActiveAttempt(expectedAttemptId, now);

        if (attemptCount >= MAX_ATTEMPTS) {
            throw new IllegalStateException("초안 생성 최대 시도 횟수에 도달했습니다.");
        }

        this.status = CoverLetterDraftStatus.PENDING;
        this.availableAt = Objects.requireNonNull(nextAvailableAt, "nextAvailableAt는 필수입니다.");

        clearExecution();
        this.failureCode = null;
        this.updatedAt = now;
    }


    public void complete(String expectedAttemptId, GenerateDraft generated, LocalDateTime now) {
        requireActiveAttempt(expectedAttemptId, now);
        Objects.requireNonNull(generated, "generated는 필수입니다.");

        this.generatedTitle = requireText(generated.title(), "generatedTitle", 100);
        this.generatedContent = requireText(generated.content(), "generatedContent", 20_000);
        this.changeSummary = requireText(generated.changeSummary(), "changeSummary", 1000);
        this.warnings = validateWarnings(generated.warnings());

        this.status = CoverLetterDraftStatus.REVIEW_READY;
        this.generatedAt = now;
        this.failureCode = null;

        clearExecution();
        this.updatedAt = now;
    }


    public void fail(String expectedAttemptId, String failureCode, LocalDateTime now) {
        requireActiveAttempt(expectedAttemptId, now);

        this.status = CoverLetterDraftStatus.FAILED;
        this.failureCode = requireFailureCode(failureCode);

        clearExecution();
        this.updatedAt = now;
    }


    public void failExpired(String failureCode, LocalDateTime now) {
        if (status != CoverLetterDraftStatus.RUNNING || leaseExpiresAt == null || leaseExpiresAt.isAfter(now)) {
            throw new IllegalStateException("만료된 실행 중 초안이 아닙니다.");
        }

        this.status = CoverLetterDraftStatus.FAILED;
        this.failureCode = requireFailureCode(failureCode);

        clearExecution();
        this.updatedAt = now;
    }


    public void apply(Integer versionNumber, String title, String content, LocalDateTime now) {
        if (status != CoverLetterDraftStatus.REVIEW_READY) {
            throw new IllegalStateException("검수 대기 초안만 적용할 수 있습니다.");
        }

        this.appliedVersionNumber = requirePositive(versionNumber, "appliedVersionNumber");
        this.appliedTitle = requireText(title, "appliedTitle", 100);
        this.appliedContent = requireText(content, "appliedContent", 20_000);
        this.appliedAt = Objects.requireNonNull(now, "now는 필수입니다.");
        this.status = CoverLetterDraftStatus.APPLIED;
        this.updatedAt = now;
    }


    public boolean hasSameAppliedContent(String title, String content) {
        return status == CoverLetterDraftStatus.APPLIED
                && Objects.equals(appliedTitle, normalizeRequired(title))
                && Objects.equals(appliedContent, normalizeRequired(content));
    }


    private void requireActiveAttempt(String expectedAttemptId, LocalDateTime now) {
        boolean inactive = status != CoverLetterDraftStatus.RUNNING
                || !Objects.equals(attemptId, expectedAttemptId)
                || leaseExpiresAt == null
                || !leaseExpiresAt.isAfter(now);

        if (inactive) {
            throw new IllegalStateException("유효하지 않거나 만료된 초안 생성 실행입니다.");
        }
    }


    private void clearExecution() {
        attemptId = null;
        leaseExpiresAt = null;
    }


    private void validateResumeSnapshot() {
        boolean allMissing = resumeId == null && resumeTitle == null && resumeContent == null;
        boolean allPresent = resumeId != null && resumeTitle != null && resumeContent != null;

        if (!allMissing && !allPresent) {
            throw new IllegalArgumentException("이력서 스냅샷은 ID, 제목, 본문이 모두 있거나 모두 없어야 합니다.");
        }
    }


    public record Attempt(int number, String id) {

    }


    public record GenerateDraft(String title, String content, String changeSummary, List<String> warnings) {

    }


    public record InputSnapshot(
            Long jobPostingId,
            Long resumeId,
            Integer baseVersionNumber,
            String coverLetterTitle,
            String coverLetterContent,
            Long companyId,
            String companyName,
            String companyIndustry,
            String companyDescription,
            String companyWebsiteUrl,
            String companyLocation,
            String jobPostingTitle,
            String jobRole,
            String employmentType,
            String jobPostingLocation,
            String jobPostingDescription,
            String jobPostingSourceUrl,
            LocalDateTime jobPostingOpensAt,
            LocalDateTime jobPostingClosesAt,
            String resumeTitle,
            String resumeContent,
            String instruction
    ) {

    }
}
