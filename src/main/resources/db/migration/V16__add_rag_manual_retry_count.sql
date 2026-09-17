ALTER TABLE rag_index_jobs
    ADD COLUMN manual_retry_count INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_rag_index_jobs_manual_retry_count
        CHECK (manual_retry_count BETWEEN 0 AND 2);