CREATE TABLE interview_sessions
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    job_posting_id       BIGINT       NOT NULL,
    cover_letter_id      BIGINT       NULL,
    resume_id            BIGINT       NULL,
    status               VARCHAR(20)  NOT NULL,

    company_name         VARCHAR(100) NOT NULL,
    job_posting_title    VARCHAR(200) NOT NULL,
    job_role             VARCHAR(100) NOT NULL,
    job_posting_content  MEDIUMTEXT   NOT NULL,

    cover_letter_title   VARCHAR(100) NULL,
    cover_letter_content MEDIUMTEXT   NULL,
    resume_title         VARCHAR(100) NULL,
    resume_content       MEDIUMTEXT   NULL,

    failure_code         VARCHAR(50)  NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,

    CONSTRAINT pk_interview_sessions PRIMARY KEY (id),

    CONSTRAINT fk_interview_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_interview_sessions_status
        CHECK (status IN (
                          'GENERATING',
                          'READY',
                          'IN_PROGRESS',
                          'COMPLETED',
                          'FAILED'
            )),

    CONSTRAINT chk_interview_sessions_cover_letter_snapshot
        CHECK (
            (cover_letter_id IS NULL
                AND cover_letter_title IS NULL
                AND cover_letter_content IS NULL)
                OR
            (cover_letter_id IS NOT NULL
                AND cover_letter_title IS NOT NULL
                AND cover_letter_content IS NOT NULL)
            ),

    CONSTRAINT chk_interview_sessions_resume_snapshot
        CHECK (
            (resume_id IS NULL
                AND resume_title IS NULL
                AND resume_content IS NULL)
                OR
            (resume_id IS NOT NULL
                AND resume_title IS NOT NULL
                AND resume_content IS NOT NULL)
            ),

    CONSTRAINT chk_interview_sessions_failure
        CHECK (
            (status = 'FAILED' AND failure_code IS NOT NULL)
                OR
            (status <> 'FAILED' AND failure_code IS NULL)
            ),

    INDEX idx_interview_sessions_user_created_at
        (user_id, created_at),

    INDEX idx_interview_sessions_user_status
        (user_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_questions
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    session_id        BIGINT      NOT NULL,
    sequence_number   INT         NOT NULL,
    question_type     VARCHAR(20) NOT NULL,
    generation_source VARCHAR(20) NOT NULL,
    content           MEDIUMTEXT  NOT NULL,
    context_snapshot  MEDIUMTEXT  NULL,
    created_at        DATETIME(6) NOT NULL,

    CONSTRAINT pk_interview_questions PRIMARY KEY (id),

    CONSTRAINT uk_interview_questions_session_sequence
        UNIQUE (session_id, sequence_number),

    CONSTRAINT fk_interview_questions_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_interview_questions_sequence
        CHECK (sequence_number >= 1),

    CONSTRAINT chk_interview_questions_type
        CHECK (question_type IN (
                                 'TECHNICAL',
                                 'BEHAVIORAL',
                                 'FOLLOW_UP'
            )),

    CONSTRAINT chk_interview_questions_generation_source
        CHECK (generation_source IN (
                                     'AI',
                                     'FALLBACK'
            )),

    INDEX idx_interview_questions_session
        (session_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;