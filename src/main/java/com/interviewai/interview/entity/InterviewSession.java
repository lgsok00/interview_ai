package com.interviewai.interview.entity;

import com.interviewai.interview.enums.InterviewSessionStatus;
import com.interviewai.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Table(name = "interview_sessions")
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "job_posting_id", nullable = false, updatable = false)
    private Long jobPostingId;

    @Column(name = "cover_letter_id", updatable = false)
    private Long coverLetterId;

    @Column(name = "resume_id", updatable = false)
    private Long resumeId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private InterviewSessionStatus status;

    @Column(name = "company_name", nullable = false, length = 100, updatable = false)
    private String companyName;

    @Column(name = "job_posting_title", nullable = false, length = 200, updatable = false)
    private String jobPostingTitle;

    @Column(name = "job_role", nullable = false, length = 100, updatable = false)
    private String jobRole;

    @Column(name = "job_posting_content", nullable = false, columnDefinition = "MEDIUMTEXT", updatable = false)
    private String jobPostingContent;

    @Column(name = "cover_letter_title", length = 100, updatable = false)
    private String coverLetterTitle;

    @Column(name = "cover_letter_content", columnDefinition = "MEDIUMTEXT", updatable = false)
    private String coverLetterContent;

    @Column(name = "resume_title", length = 100, updatable = false)
    private String resumeTitle;

    @Column(name = "resume_content", columnDefinition = "MEDIUMTEXT", updatable = false)
    private String resumeContent;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    protected InterviewSession() {

    }


    private InterviewSession(
            User user,
            Long jobPostingId,
            Long coverLetterId,
            Long resumeId,
            String companyName,
            String jobPostingTitle,
            String jobRole,
            String jobPostingContent,
            String coverLetterTitle,
            String coverLetterContent,
            String resumeTitle,
            String resumeContent,
            LocalDateTime now
    ) {
        this.user = Objects.requireNonNull(user, "user는 필수입니다.");
        this.jobPostingId = requirePositive(jobPostingId);
        this.coverLetterId = optionalPositive(coverLetterId, "coverLetterId");
        this.resumeId = optionalPositive(resumeId, "resumeId");
        this.companyName = requireText(companyName, "companyName");
        this.jobPostingTitle = requireText(jobPostingTitle, "jobPostingTitle");
        this.jobRole = requireText(jobRole, "jobRole");
        this.jobPostingContent = requireText(jobPostingContent, "jobPostingContent");
        this.coverLetterTitle = coverLetterTitle;
        this.coverLetterContent = coverLetterContent;
        this.resumeTitle = resumeTitle;
        this.resumeContent = resumeContent;
        this.status = InterviewSessionStatus.GENERATING;
        this.createdAt = Objects.requireNonNull(now, "now는 필수입니다.");
        this.updatedAt = now;

        validateOptionalSnapshot(coverLetterId, coverLetterTitle, coverLetterContent, "자기소개서");
        validateOptionalSnapshot(resumeId, resumeTitle, resumeContent, "이력서");
    }


    public static InterviewSession create(
            User user,
            Long jobPostingId,
            Long coverLetterId,
            Long resumeId,
            String companyName,
            String jobPostingTitle,
            String jobRole,
            String jobPostingContent,
            String coverLetterTitle,
            String coverLetterContent,
            String resumeTitle,
            String resumeContent,
            LocalDateTime now
    ) {
        return new InterviewSession(
                user,
                jobPostingId,
                coverLetterId,
                resumeId,
                companyName,
                jobPostingTitle,
                jobRole,
                jobPostingContent,
                coverLetterTitle,
                coverLetterContent,
                resumeTitle,
                resumeContent,
                now
        );
    }


    public void markReady(LocalDateTime now) {
        requireStatus(InterviewSessionStatus.GENERATING);

        this.status = InterviewSessionStatus.READY;
        this.updatedAt = Objects.requireNonNull(now, "now는 필수입니다.");
    }


    public void start(LocalDateTime now) {
        requireStatus(InterviewSessionStatus.READY);

        this.status = InterviewSessionStatus.IN_PROGRESS;
        this.updatedAt = Objects.requireNonNull(now, "now는 필수입니다.");
    }


    public void complete(LocalDateTime now) {
        requireStatus(InterviewSessionStatus.IN_PROGRESS);

        this.status = InterviewSessionStatus.COMPLETED;
        this.updatedAt = Objects.requireNonNull(now, "now는 필수입니다.");
    }


    public void fail(String failureCode, LocalDateTime now) {
        requireStatus(InterviewSessionStatus.GENERATING);

        this.failureCode = requireText(failureCode, "failureCode");
        this.status = InterviewSessionStatus.FAILED;
        this.updatedAt = Objects.requireNonNull(now, "now는 필수입니다.");
    }


    public void retry(LocalDateTime now) {
        requireStatus(InterviewSessionStatus.FAILED);

        this.failureCode = null;
        this.status = InterviewSessionStatus.GENERATING;
        this.updatedAt = Objects.requireNonNull(now, "now는 필수입니다.");
    }


    private static Long requirePositive(Long value) {
        Objects.requireNonNull(value, "jobPostingId는 필수입니다.");

        if (value <= 0) {
            throw new IllegalArgumentException("jobPostingId는 1 이상이어야 합니다.");
        }

        return value;
    }


    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + "는 필수입니다.");

        if (value.isBlank()) {
            throw new IllegalArgumentException(field + "는 비어 있을 수 없습니다.");
        }

        return value;
    }


    private void requireStatus(InterviewSessionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("면접 세션 상태가 올바르지 않습니다. expected=" + expected + ", actual=" + status);
        }
    }


    private static Long optionalPositive(Long value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(field + "는 1 이상이어야 합니다.");
        }

        return value;
    }


    private static void validateOptionalSnapshot(Long sourceId, String title, String content, String sourceName) {
        boolean allMissing = sourceId == null && title == null && content == null;
        boolean allPresent = sourceId != null
                && title != null && !title.isBlank()
                && content != null && !content.isBlank();

        if (!allMissing && !allPresent) {
            throw new IllegalArgumentException(sourceName + " 스냅샷은 ID, 제목, 본문이 모두 있거나 모두 없어야 합니다.");
        }
    }
}
