ALTER TABLE rag_index_jobs
    ADD COLUMN attempt_id VARCHAR(36)
                              CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN lease_expires_at DATETIME(6) NULL,
    ADD COLUMN available_at DATETIME(6) NULL,
    ADD COLUMN failure_code VARCHAR(100) NULL,

    ADD CONSTRAINT chk_rag_index_jobs_lease
        CHECK (
            (
                status = 'RUNNING'
                    AND attempt_id IS NOT NULL
                    AND lease_expires_at IS NOT NULL
                )
                OR
            (
                status <> 'RUNNING'
                    AND lease_expires_at IS NULL
                )
            ),

    ADD INDEX idx_rag_index_jobs_available
        (status, available_at, id),

    ADD INDEX idx_rag_index_jobs_lease
        (status, lease_expires_at, id);