CREATE TABLE external_collection_requests
(
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    collection_kind       VARCHAR(20)   NOT NULL,
    source_url            VARCHAR(2048) NOT NULL,
    normalized_source_url VARCHAR(2048) NOT NULL,

    requested_by_user_id  BIGINT        NULL,
    status                VARCHAR(20)   NOT NULL,

    attempt_count         INT           NOT NULL DEFAULT 0,
    manual_retry_count    INT           NOT NULL DEFAULT 0,
    attempt_id            VARCHAR(36)   NULL,
    lease_expires_at      DATETIME(6)   NULL,
    available_at          DATETIME(6)   NULL,

    last_error_code       VARCHAR(50)   NULL,

    approved_snapshot_id  BIGINT        NULL,
    approved_target_type  VARCHAR(20)   NULL,
    approved_target_id    BIGINT        NULL,
    approved_by_user_id   BIGINT        NULL,
    approved_at           DATETIME(6)   NULL,

    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,

    CONSTRAINT pk_external_collection_requests
        PRIMARY KEY (id),

    CONSTRAINT fk_external_collection_requests_requested_by
        FOREIGN KEY (requested_by_user_id)
            REFERENCES users (id)
            ON DELETE SET NULL,

    CONSTRAINT fk_external_collection_requests_approved_by
        FOREIGN KEY (approved_by_user_id)
            REFERENCES users (id)
            ON DELETE SET NULL,

    CONSTRAINT chk_external_collection_requests_kind
        CHECK (collection_kind IN ('COMPANY', 'JOB_POSTING')),

    CONSTRAINT chk_external_collection_requests_status
        CHECK (status IN (
                          'PENDING', 'RUNNING', 'REVIEW_READY',
                          'FAILED', 'APPROVED', 'REJECTED'
            )),

    CONSTRAINT chk_external_collection_requests_counts
        CHECK (
            attempt_count BETWEEN 0 AND 3
                AND manual_retry_count BETWEEN 0 AND 2
            ),

    CONSTRAINT chk_external_collection_requests_lease
        CHECK (
            (status = 'RUNNING'
                AND attempt_id IS NOT NULL
                AND lease_expires_at IS NOT NULL)
                OR
            (status <> 'RUNNING'
                AND attempt_id IS NULL
                AND lease_expires_at IS NULL)
            ),

    CONSTRAINT chk_external_collection_requests_approval
        CHECK (
            (status = 'APPROVED'
                AND approved_snapshot_id IS NOT NULL
                AND approved_target_type IS NOT NULL
                AND approved_target_id IS NOT NULL
                AND approved_at IS NOT NULL)
                OR
            (status <> 'APPROVED'
                AND approved_snapshot_id IS NULL
                AND approved_target_type IS NULL
                AND approved_target_id IS NULL
                AND approved_at IS NULL)
            ),

    CONSTRAINT chk_external_collection_requests_approved_target
        CHECK (
            approved_target_type IS NULL
                OR approved_target_type IN ('COMPANY', 'JOB_POSTING')
            ),

    INDEX idx_external_collection_requests_pending
        (status, available_at),

    INDEX idx_external_collection_requests_lease
        (status, lease_expires_at),

    INDEX idx_external_collection_requests_created
        (created_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE external_collection_snapshots
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    request_id     BIGINT        NOT NULL,
    attempt_number INT           NOT NULL,
    attempt_id     VARCHAR(36)   NOT NULL,
    status         VARCHAR(20)   NOT NULL,

    final_url      VARCHAR(2048) NULL,
    http_status    INT           NULL,
    content_type   VARCHAR(100)  NULL,
    body_sha256    CHAR(64)      NULL,
    extracted_text MEDIUMTEXT    NULL,
    candidate_json JSON          NULL,
    evidence_json  JSON          NULL,
    parser_version VARCHAR(30)   NULL,

    failure_code   VARCHAR(50)   NULL,
    collected_at   DATETIME(6)   NOT NULL,

    CONSTRAINT pk_external_collection_snapshots
        PRIMARY KEY (id),

    CONSTRAINT fk_external_collection_snapshots_request
        FOREIGN KEY (request_id)
            REFERENCES external_collection_requests (id)
            ON DELETE CASCADE,

    CONSTRAINT uk_external_collection_snapshots_request_attempt
        UNIQUE (request_id, attempt_number),

    CONSTRAINT uk_external_collection_snapshots_attempt_id
        UNIQUE (attempt_id),

    CONSTRAINT chk_external_collection_snapshots_attempt_number
        CHECK (attempt_number BETWEEN 1 AND 3),

    CONSTRAINT chk_external_collection_snapshots_status
        CHECK (status IN ('SUCCEEDED', 'FAILED')),

    CONSTRAINT chk_external_collection_snapshots_http_status
        CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),

    CONSTRAINT chk_external_collection_snapshots_success
        CHECK (
            (status = 'SUCCEEDED'
                AND final_url IS NOT NULL
                AND http_status BETWEEN 200 AND 299
                AND content_type IS NOT NULL
                AND body_sha256 IS NOT NULL
                AND extracted_text IS NOT NULL
                AND parser_version IS NOT NULL
                AND failure_code IS NULL)
                OR
            (status = 'FAILED'
                AND failure_code IS NOT NULL)
            ),

    CONSTRAINT chk_external_collection_snapshots_text_length
        CHECK (
            extracted_text IS NULL
                OR CHAR_LENGTH(extracted_text) <= 100000
            ),

    INDEX idx_external_collection_snapshots_request_collected
        (request_id, collected_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;