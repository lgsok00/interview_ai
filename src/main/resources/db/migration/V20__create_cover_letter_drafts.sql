CREATE TABLE cover_letter_drafts
(
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT        NOT NULL,
    cover_letter_id         BIGINT        NOT NULL,
    source_draft_id         BIGINT        NULL,

    job_posting_id          BIGINT        NOT NULL,
    resume_id               BIGINT        NULL,
    base_version_number     INT           NOT NULL,

    cover_letter_title      VARCHAR(100)  NOT NULL,
    cover_letter_content    MEDIUMTEXT    NOT NULL,

    company_id              BIGINT        NOT NULL,
    company_name            VARCHAR(100)  NOT NULL,
    company_industry        VARCHAR(100)  NULL,
    company_description     MEDIUMTEXT    NOT NULL,
    company_website_url     VARCHAR(2048) NULL,
    company_location        VARCHAR(200)  NULL,

    job_posting_title       VARCHAR(200)  NOT NULL,
    job_role                VARCHAR(100)  NOT NULL,
    employment_type         VARCHAR(20)   NOT NULL,
    job_posting_location    VARCHAR(200)  NULL,
    job_posting_description MEDIUMTEXT    NOT NULL,
    job_posting_source_url  VARCHAR(2048) NULL,
    job_posting_opens_at    DATETIME(6)   NULL,
    job_posting_closes_at   DATETIME(6)   NULL,

    resume_title            VARCHAR(100)  NULL,
    resume_content          MEDIUMTEXT    NULL,
    instruction             VARCHAR(1000) NULL,

    input_hash              CHAR(64)      NOT NULL,
    prompt_template_version VARCHAR(50)   NOT NULL,
    model                   VARCHAR(100)  NOT NULL,

    status                  VARCHAR(20)   NOT NULL,

    generated_title         VARCHAR(100)  NULL,
    generated_content       MEDIUMTEXT    NULL,
    change_summary          VARCHAR(1000) NULL,
    warnings_json           JSON          NULL,

    attempt_count           INT           NOT NULL DEFAULT 0,
    available_at            DATETIME(6)   NOT NULL,
    attempt_id              VARCHAR(36)   NULL,
    lease_expires_at        DATETIME(6)   NULL,
    failure_code            VARCHAR(50)   NULL,

    generated_at            DATETIME(6)   NULL,
    applied_version_number  INT           NULL,
    applied_title           VARCHAR(100)  NULL,
    applied_content         MEDIUMTEXT    NULL,
    applied_at              DATETIME(6)   NULL,

    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,

    CONSTRAINT pk_cover_letter_drafts
        PRIMARY KEY (id),

    CONSTRAINT fk_cover_letter_drafts_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_cover_letter_drafts_cover_letter
        FOREIGN KEY (cover_letter_id)
            REFERENCES cover_letters (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_cover_letter_drafts_source
        FOREIGN KEY (source_draft_id)
            REFERENCES cover_letter_drafts (id)
            ON DELETE SET NULL,

    CONSTRAINT chk_cover_letter_drafts_base_version
        CHECK (base_version_number >= 1),

    CONSTRAINT chk_cover_letter_drafts_status
        CHECK (status IN (
                          'PENDING',
                          'RUNNING',
                          'REVIEW_READY',
                          'FAILED',
                          'APPLIED'
            )),

    CONSTRAINT chk_cover_letter_drafts_attempt_count
        CHECK (attempt_count BETWEEN 0 AND 3),

    CONSTRAINT chk_cover_letter_drafts_lease
        CHECK (
            (
                status = 'RUNNING'
                    AND attempt_id IS NOT NULL
                    AND lease_expires_at IS NOT NULL
                )
                OR
            (
                status <> 'RUNNING'
                    AND attempt_id IS NULL
                    AND lease_expires_at IS NULL
                )
            ),

    CONSTRAINT chk_cover_letter_drafts_resume_snapshot
        CHECK (
            (
                resume_id IS NULL
                    AND resume_title IS NULL
                    AND resume_content IS NULL
                )
                OR
            (
                resume_id IS NOT NULL
                    AND resume_title IS NOT NULL
                    AND resume_content IS NOT NULL
                    AND CHAR_LENGTH(TRIM(resume_content)) > 0
                )
            ),

    CONSTRAINT chk_cover_letter_drafts_generated_result
        CHECK (
            (
                status IN ('REVIEW_READY', 'APPLIED')
                    AND generated_title IS NOT NULL
                    AND generated_content IS NOT NULL
                    AND change_summary IS NOT NULL
                    AND warnings_json IS NOT NULL
                    AND generated_at IS NOT NULL
                    AND failure_code IS NULL
                )
                OR
            (
                status NOT IN ('REVIEW_READY', 'APPLIED')
                    AND generated_title IS NULL
                    AND generated_content IS NULL
                    AND change_summary IS NULL
                    AND warnings_json IS NULL
                    AND generated_at IS NULL
                )
            ),

    CONSTRAINT chk_cover_letter_drafts_failure
        CHECK (
            (status = 'FAILED' AND failure_code IS NOT NULL)
                OR
            (status <> 'FAILED' AND failure_code IS NULL)
            ),

    CONSTRAINT chk_cover_letter_drafts_application
        CHECK (
            (
                status = 'APPLIED'
                    AND applied_version_number IS NOT NULL
                    AND applied_title IS NOT NULL
                    AND applied_content IS NOT NULL
                    AND applied_at IS NOT NULL
                )
                OR
            (
                status <> 'APPLIED'
                    AND applied_version_number IS NULL
                    AND applied_title IS NULL
                    AND applied_content IS NULL
                    AND applied_at IS NULL
                )
            ),

    CONSTRAINT chk_cover_letter_drafts_instruction_length
        CHECK (
            instruction IS NULL
                OR CHAR_LENGTH(instruction) BETWEEN 1 AND 1000
            ),

    CONSTRAINT chk_cover_letter_drafts_generated_lengths
        CHECK (
            (generated_title IS NULL
                OR CHAR_LENGTH(TRIM(generated_title)) BETWEEN 1 AND 100)
                AND
            (generated_content IS NULL
                OR CHAR_LENGTH(TRIM(generated_content)) BETWEEN 1 AND 20000)
                AND
            (change_summary IS NULL
                OR CHAR_LENGTH(TRIM(change_summary)) BETWEEN 1 AND 1000)
            ),

    CONSTRAINT chk_cover_letter_drafts_applied_lengths
        CHECK (
            (applied_title IS NULL
                OR CHAR_LENGTH(TRIM(applied_title)) BETWEEN 1 AND 100)
                AND
            (applied_content IS NULL
                OR CHAR_LENGTH(TRIM(applied_content)) BETWEEN 1 AND 20000)
            ),

    CONSTRAINT chk_cover_letter_drafts_input_hash
        CHECK (input_hash REGEXP '^[0-9a-f]{64}$'),

    INDEX idx_cover_letter_drafts_owner_created
        (user_id, cover_letter_id, created_at, id),

    INDEX idx_cover_letter_drafts_pending
        (status, available_at, id),

    INDEX idx_cover_letter_drafts_lease
        (status, lease_expires_at, id),

    INDEX idx_cover_letter_drafts_source
        (source_draft_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;