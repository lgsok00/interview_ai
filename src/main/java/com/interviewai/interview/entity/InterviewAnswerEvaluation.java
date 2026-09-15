package com.interviewai.interview.entity;

import com.interviewai.interview.enums.AnswerEvaluationStatus;
import com.interviewai.interview.generation.InterviewGenerationPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Table(
        name = "interview_answer_evaluations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interview_answer_evaluations_answer",
                columnNames = "answer_id"
        )
)
public class InterviewAnswerEvaluation {

    public static final int MAX_ATTEMPTS = 3;
    public static final int MAX_MANUAL_RETRIES = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "answer_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private InterviewAnswer answer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AnswerEvaluationStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, updatable = false)
    private InterviewGenerationPolicy.Mode mode;

    @Column(length = 100, updatable = false)
    private String model;

    @Column(
            name = "pipeline_version",
            nullable = false,
            length = 50,
            updatable = false
    )
    private String pipelineVersion;

    @Column(name = "star_score")
    private Integer starScore;

    @Column(name = "logic_score")
    private Integer logicScore;

    @Column(name = "job_fit_score")
    private Integer jobFitScore;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String strengths;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String improvements;

    @Column(name = "improved_answer", columnDefinition = "MEDIUMTEXT")
    private String improvedAnswer;

    @Column(name = "context_snapshot", columnDefinition = "MEDIUMTEXT")
    private String contextSnapshot;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "manual_retry_count", nullable = false)
    private int manualRetryCount;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "attempt_id", length = 36)
    private String attemptId;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;


    protected InterviewAnswerEvaluation() {

    }


    private InterviewAnswerEvaluation(
            InterviewAnswer answer,
            InterviewGenerationPolicy.Mode mode,
            String model,
            String pipelineVersion,
            LocalDateTime now
    ) {
        this.answer = Objects.requireNonNull(answer, "answer는 필수입니다.");
        this.mode = Objects.requireNonNull(mode, "mode는 필수입니다.");
        this.model = normalizeModel(mode, model);
        this.pipelineVersion = requireText(pipelineVersion, "pipelineVersion", 50);
        this.status = AnswerEvaluationStatus.PENDING;
        this.availableAt = Objects.requireNonNull(now, "now는 필수입니다.");
        this.createdAt = now;
        this.updatedAt = now;
    }


    public static InterviewAnswerEvaluation create(
            InterviewAnswer answer,
            InterviewGenerationPolicy.Mode mode,
            String model,
            String pipelineVersion,
            LocalDateTime now
    ) {
        return new InterviewAnswerEvaluation(
                answer,
                mode,
                model,
                pipelineVersion,
                now
        );
    }


    private static String requireAttemptId(String value) {
        String normalized = requireText(value, "attemptId", 36);

        if (!normalized.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("attemptId 형식이 올바르지 않습니다.");
        }

        return normalized;
    }


    private static int requireScore(int value, String field) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(field + "는 0 이상 100 이하여야 합니다.");
        }

        return value;
    }


    private static String requireText(String value, String field, int maxLength) {
        String normalized = Objects.requireNonNull(value, field + "는 필수입니다.").strip();

        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "는 1자 이상 " + maxLength + "자 이하여야 합니다.");
        }

        return normalized;
    }


    private static String normalizeModel(InterviewGenerationPolicy.Mode mode, String value) {
        String normalized = value == null ? "" : value.strip();

        if (normalized.length() > 100) {
            throw new IllegalArgumentException("model은 100자 이하여야 합니다.");
        }

        if (mode == InterviewGenerationPolicy.Mode.AI && normalized.isBlank()) {
            throw new IllegalArgumentException("AI 평가에는 model이 필요합니다.");
        }

        return normalized.isBlank() ? null : normalized;
    }


    public void claim(String attemptId, LocalDateTime now, LocalDateTime leaseExpiresAt) {
        Objects.requireNonNull(now, "now는 필수입니다.");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt은 필수입니다.");

        if (status != AnswerEvaluationStatus.PENDING) {
            throw new IllegalStateException("대기 중인 평가만 선점할 수 있습니다.");
        }

        if (availableAt.isAfter(now)) {
            throw new IllegalStateException("아직 실행할 수 없는 평가입니다.");
        }

        if (attemptCount >= MAX_ATTEMPTS) {
            throw new IllegalStateException("평가 최대 시도 횟수를 초과했습니다.");
        }

        if (!leaseExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("lease 만료 시각은 현재 시각 이후여야 합니다.");
        }

        this.attemptId = requireAttemptId(attemptId);
        this.status = AnswerEvaluationStatus.PROCESSING;
        this.attemptCount++;
        this.leaseExpiresAt = leaseExpiresAt;
        this.failureCode = null;
        this.updatedAt = now;
    }


    public void complete(
            String attemptId,
            int starScore,
            int logicScore,
            int jobFitScore,
            String strengths,
            String improvements,
            String improvedAnswer,
            String contextSnapshot,
            LocalDateTime now
    ) {
        requireActiveAttempt(attemptId, now);

        int validatedStarScore = requireScore(starScore, "starScore");
        int validatedLogicScore = requireScore(logicScore, "logicScore");
        int validatedJobFitScore = requireScore(jobFitScore, "jobFitScore");
        String validatedStrengths = requireText(strengths, "strengths", 10000);
        String validatedImprovements = requireText(improvements, "improvements", 10000);
        String validatedImprovedAnswer = requireText(improvedAnswer, "improvedAnswer", 10000);
        String validatedContextSnapshot = requireText(contextSnapshot, "contextSnapshot", 50000);

        this.starScore = validatedStarScore;
        this.logicScore = validatedLogicScore;
        this.jobFitScore = validatedJobFitScore;
        this.strengths = validatedStrengths;
        this.improvements = validatedImprovements;
        this.improvedAnswer = validatedImprovedAnswer;
        this.contextSnapshot = validatedContextSnapshot;
        this.status = AnswerEvaluationStatus.COMPLETED;
        this.attemptId = null;
        this.leaseExpiresAt = null;
        this.failureCode = null;
        this.completedAt = now;
        this.updatedAt = now;
    }


    public void retry(String attemptId, String failureCode, LocalDateTime availableAt, LocalDateTime now) {
        requireActiveAttempt(attemptId, now);

        if (attemptCount >= MAX_ATTEMPTS) {
            throw new IllegalStateException("평가 최대 시도 횟수에 도달했습니다.");
        }

        String validatedFailureCode = requireText(failureCode, "failureCode", 50);
        Objects.requireNonNull(availableAt, "availableAt은 필수입니다.");

        this.status = AnswerEvaluationStatus.PENDING;
        this.attemptId = null;
        this.failureCode = validatedFailureCode;
        this.availableAt = availableAt;
        this.leaseExpiresAt = null;
        this.updatedAt = now;
    }


    public void fail(String attemptId, String failureCode, LocalDateTime now) {
        requireActiveAttempt(attemptId, now);

        String validatedFailureCode = requireText(failureCode, "failureCode", 50);

        this.status = AnswerEvaluationStatus.FAILED;
        this.attemptId = null;
        this.failureCode = validatedFailureCode;
        this.leaseExpiresAt = null;
        this.updatedAt = now;
    }


    public void expireLease(String failureCode, LocalDateTime availableAt, LocalDateTime now) {
        requireProcessing();
        Objects.requireNonNull(now, "now는 필수입니다.");

        if (leaseExpiresAt == null || leaseExpiresAt.isAfter(now)) {
            throw new IllegalStateException("만료된 lease가 아닙니다.");
        }

        String validatedFailureCode = requireText(failureCode, "failureCode", 50);

        if (attemptCount >= MAX_ATTEMPTS) {
            this.status = AnswerEvaluationStatus.FAILED;

        } else {
            Objects.requireNonNull(availableAt, "availableAt은 필수입니다.");

            this.status = AnswerEvaluationStatus.PENDING;
            this.availableAt = availableAt;
        }

        this.attemptId = null;
        this.failureCode = validatedFailureCode;
        this.leaseExpiresAt = null;
        this.updatedAt = now;
    }


    public void manualRetry(LocalDateTime now) {
        Objects.requireNonNull(now, "now는 필수입니다.");

        if (status != AnswerEvaluationStatus.FAILED) {
            throw new IllegalStateException("실패한 평가만 다시 요청할 수 있습니다.");
        }

        if (manualRetryCount >= MAX_MANUAL_RETRIES) {
            throw new IllegalStateException("수동 재시도 횟수를 초과했습니다.");
        }

        this.status = AnswerEvaluationStatus.PENDING;
        this.attemptId = null;
        this.attemptCount = 0;
        this.manualRetryCount++;
        this.availableAt = now;
        this.leaseExpiresAt = null;
        this.failureCode = null;
        this.updatedAt = now;
    }


    private void requireProcessing() {
        if (status != AnswerEvaluationStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 평가만 결과를 반영할 수 있습니다.");
        }
    }


    private void requireActiveAttempt(String attemptId, LocalDateTime now) {
        Objects.requireNonNull(now, "now는 필수입니다.");
        requireProcessing();

        if (attemptId == null || !attemptId.equals(this.attemptId)) {
            throw new IllegalStateException("현재 실행 중인 평가 시도가 아닙니다.");
        }

        if (leaseExpiresAt == null || !leaseExpiresAt.isAfter(now)) {
            throw new IllegalStateException("평가 작업의 lease가 만료되었습니다.");
        }
    }
}
