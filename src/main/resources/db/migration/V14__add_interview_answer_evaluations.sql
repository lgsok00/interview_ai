CREATE TABLE interview_answer_evaluations
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    answer_id          BIGINT       NOT NULL,

    status             VARCHAR(20)  NOT NULL,
    mode               VARCHAR(20)  NOT NULL,
    model              VARCHAR(100) NULL,
    pipeline_version   VARCHAR(50)  NOT NULL,

    star_score         INT          NULL,
    logic_score        INT          NULL,
    job_fit_score      INT          NULL,
    strengths          MEDIUMTEXT   NULL,
    improvements       MEDIUMTEXT   NULL,
    improved_answer    MEDIUMTEXT   NULL,
    context_snapshot   MEDIUMTEXT   NULL,

    attempt_count      INT          NOT NULL DEFAULT 0,
    manual_retry_count INT          NOT NULL DEFAULT 0,
    available_at       DATETIME(6)  NOT NULL,
    attempt_id         VARCHAR(36)  NULL,
    lease_expires_at   DATETIME(6)  NULL,
    failure_code       VARCHAR(50)  NULL,

    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    completed_at       DATETIME(6)  NULL,

    CONSTRAINT pk_interview_answer_evaluations
        PRIMARY KEY (id),

    CONSTRAINT uk_interview_answer_evaluations_answer
        UNIQUE (answer_id),

    CONSTRAINT fk_interview_answer_evaluations_answer
        FOREIGN KEY (answer_id) REFERENCES interview_answers (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_interview_answer_evaluations_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),

    CONSTRAINT chk_interview_answer_evaluations_mode
        CHECK (mode IN ('AI', 'FALLBACK_ONLY')),

    CONSTRAINT chk_interview_answer_evaluations_scores
        CHECK (
            (star_score IS NULL OR star_score BETWEEN 0 AND 100)
                AND (logic_score IS NULL OR logic_score BETWEEN 0 AND 100)
                AND (job_fit_score IS NULL OR job_fit_score BETWEEN 0 AND 100)
            ),

    CONSTRAINT chk_interview_answer_evaluations_counts
        CHECK (
            attempt_count BETWEEN 0 AND 3
                AND manual_retry_count BETWEEN 0 AND 2
            ),

    CONSTRAINT chk_interview_answer_evaluations_lease
        CHECK (
            (
                status = 'PROCESSING'
                    AND attempt_id IS NOT NULL
                    AND lease_expires_at IS NOT NULL
                )
                OR
            (
                status <> 'PROCESSING'
                    AND attempt_id IS NULL
                    AND lease_expires_at IS NULL
                )
            ),

    INDEX idx_interview_answer_evaluations_pending
        (status, available_at),

    INDEX idx_interview_answer_evaluations_lease
        (status, lease_expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;