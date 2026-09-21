CREATE TABLE resumes
(
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT       NOT NULL,
    title                   VARCHAR(100) NOT NULL,
    original_filename       VARCHAR(255) NOT NULL,
    storage_key             VARCHAR(255) NOT NULL,
    content_type            VARCHAR(100) NOT NULL,
    file_size               BIGINT       NOT NULL,
    sha256                  CHAR(64)     NOT NULL,
    extracted_text          MEDIUMTEXT   NULL,
    extraction_status       VARCHAR(20)  NOT NULL,
    extraction_failure_code VARCHAR(50)  NULL,
    created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_resumes PRIMARY KEY (id),
    CONSTRAINT uk_resumes_storage_key UNIQUE (storage_key),
    CONSTRAINT uk_resumes_user_id_id UNIQUE (user_id, id),
    CONSTRAINT fk_resumes_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT chk_resumes_file_size
        CHECK (file_size > 0 AND file_size <= 10485760),
    CONSTRAINT chk_resumes_extraction_status
        CHECK (extraction_status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_resumes_extraction_result
        CHECK (
            (extraction_status = 'PENDING'
                AND extracted_text IS NULL
                AND extraction_failure_code IS NULL)
                OR
            (extraction_status = 'COMPLETED'
                AND extracted_text IS NOT NULL
                AND extraction_failure_code IS NULL)
                OR
            (extraction_status = 'FAILED'
                AND extracted_text IS NULL
                AND extraction_failure_code IS NOT NULL)
            ),

    INDEX idx_resumes_user_updated_at (user_id, updated_at),
    INDEX idx_resumes_user_sha256 (user_id, sha256)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE resume_representatives
(
    user_id    BIGINT      NOT NULL,
    resume_id  BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_resume_representatives PRIMARY KEY (user_id),
    CONSTRAINT uk_resume_representatives_resume UNIQUE (resume_id),
    CONSTRAINT fk_resume_representatives_owned_resume
        FOREIGN KEY (user_id, resume_id)
            REFERENCES resumes (user_id, id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;