CREATE TABLE rag_index_jobs
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    source_type      VARCHAR(30)  NOT NULL,
    source_id        BIGINT       NOT NULL,
    source_sequence  BIGINT       NOT NULL,
    operation        VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,

    owner_user_id    BIGINT       NULL,
    company_id       BIGINT       NULL,
    snapshot_title   LONGTEXT     NULL,
    snapshot_content LONGTEXT     NULL,
    source_revision  LONGTEXT     NULL,
    pipeline_version VARCHAR(100) NULL,

    max_attempts     INT          NOT NULL,
    attempt_count    INT          NOT NULL DEFAULT 0,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    lock_version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_rag_index_jobs PRIMARY KEY (id),

    CONSTRAINT uk_rag_index_jobs_source_sequence
        UNIQUE (source_type, source_id, source_sequence),

    CONSTRAINT fk_rag_index_jobs_source
        FOREIGN KEY (source_type, source_id)
            REFERENCES rag_index_sources (source_type, source_id),

    CONSTRAINT chk_rag_index_jobs_sequence
        CHECK (source_sequence > 0),

    CONSTRAINT chk_rag_index_jobs_operation
        CHECK (operation IN ('UPSERT', 'DELETE')),

    CONSTRAINT chk_rag_index_jobs_status
        CHECK (
            status IN (
                       'PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'
                )
            ),

    CONSTRAINT chk_rag_index_jobs_attempts
        CHECK (
            max_attempts >= 1
                AND attempt_count >= 0
                AND attempt_count <= max_attempts
            ),

    CONSTRAINT chk_rag_index_jobs_lock_version
        CHECK (lock_version >= 0),

    CONSTRAINT chk_rag_index_jobs_time
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_rag_index_jobs_payload
        CHECK (
            (
                operation = 'UPSERT'
                    AND snapshot_title IS NOT NULL
                    AND CHAR_LENGTH(TRIM(snapshot_title)) > 0
                    AND snapshot_content IS NOT NULL
                    AND CHAR_LENGTH(TRIM(snapshot_content)) > 0
                    AND source_revision IS NOT NULL
                    AND CHAR_LENGTH(TRIM(source_revision)) > 0
                    AND pipeline_version IS NOT NULL
                    AND CHAR_LENGTH(TRIM(pipeline_version)) > 0
                    AND (
                    (
                        source_type = 'COMPANY'
                            AND owner_user_id IS NULL
                            AND company_id IS NOT NULL
                            AND company_id = source_id
                        )
                        OR (
                        source_type = 'JOB_POSTING'
                            AND owner_user_id IS NULL
                            AND company_id IS NOT NULL
                            AND company_id > 0
                        )
                        OR (
                        source_type IN ('COVER_LETTER', 'RESUME')
                            AND owner_user_id IS NOT NULL
                            AND owner_user_id > 0
                            AND company_id IS NULL
                        )
                    )
                )
                OR (
                operation = 'DELETE'
                    AND owner_user_id IS NULL
                    AND company_id IS NULL
                    AND snapshot_title IS NULL
                    AND snapshot_content IS NULL
                    AND source_revision IS NULL
                    AND pipeline_version IS NULL
                )
            ),

    INDEX idx_rag_index_jobs_status_created (status, created_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;