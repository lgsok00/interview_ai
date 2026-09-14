CREATE TABLE interview_generation_jobs
(
    session_id       BIGINT       NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    generation_mode  VARCHAR(20)  NOT NULL,
    model_name       VARCHAR(100) NULL,
    pipeline_version VARCHAR(30)  NOT NULL,

    attempt_count    INT          NOT NULL DEFAULT 0,
    retry_count      INT          NOT NULL DEFAULT 0,
    attempt_id       VARCHAR(36)  NULL,
    lease_expires_at DATETIME(6)  NULL,
    available_at     DATETIME(6)  NULL,

    last_error_code  VARCHAR(50)  NULL,
    fallback_reason  VARCHAR(50)  NULL,

    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,

    CONSTRAINT pk_interview_generation_jobs
        PRIMARY KEY (session_id),

    CONSTRAINT fk_interview_generation_jobs_session
        FOREIGN KEY (session_id)
            REFERENCES interview_sessions (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_interview_generation_jobs_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),

    CONSTRAINT chk_interview_generation_jobs_mode
        CHECK (generation_mode IN ('AI', 'FALLBACK_ONLY')),

    CONSTRAINT chk_interview_generation_jobs_model
        CHECK (
            generation_mode = 'FALLBACK_ONLY'
                OR (model_name IS NOT NULL AND CHAR_LENGTH(model_name) > 0)
            ),

    CONSTRAINT chk_interview_generation_jobs_counts
        CHECK (
            attempt_count BETWEEN 0 AND 3
                AND retry_count BETWEEN 0 AND 2
            ),

    CONSTRAINT chk_interview_generation_jobs_lease
        CHECK (
            (status = 'RUNNING'
                AND attempt_id IS NOT NULL
                AND lease_expires_at IS NOT NULL)
                OR
            (status <> 'RUNNING'
                AND lease_expires_at IS NULL)
            ),

    INDEX idx_interview_generation_jobs_pending (status, available_at),
    INDEX idx_interview_generation_jobs_lease (status, lease_expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO interview_generation_jobs
(session_id,
 status,
 generation_mode,
 pipeline_version,
 available_at,
 created_at,
 updated_at)
SELECT session.id,
       'PENDING',
       'FALLBACK_ONLY',
       'interview-v1',
       UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6)
FROM interview_sessions session
WHERE session.status = 'GENERATING'
  AND NOT EXISTS (SELECT 1
                  FROM interview_questions question
                  WHERE question.session_id = session.id);