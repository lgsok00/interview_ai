ALTER TABLE rag_index_sources
    ADD COLUMN active_generation_id VARCHAR(36) NULL,
    ADD COLUMN active_sequence      BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN tombstone_sequence   BIGINT      NOT NULL DEFAULT 0;

UPDATE rag_index_sources s
    JOIN (SELECT source_type,
                 source_id,
                 MAX(source_sequence) AS delete_sequence
          FROM rag_index_jobs
          WHERE operation = 'DELETE'
          GROUP BY source_type, source_id) d
    ON d.source_type = s.source_type
        AND d.source_id = s.source_id
SET s.tombstone_sequence = d.delete_sequence,
    s.lock_version       = s.lock_version + 1;

ALTER TABLE rag_index_sources
    ADD CONSTRAINT chk_rag_index_sources_tombstone
        CHECK (
            tombstone_sequence >= 0
                AND tombstone_sequence <= last_sequence
            ),
    ADD CONSTRAINT chk_rag_index_sources_active
        CHECK (
            (
                active_generation_id IS NULL
                    AND active_sequence = 0
                )
                OR (
                active_generation_id IS NOT NULL
                    AND CHAR_LENGTH(TRIM(active_generation_id)) = 36
                    AND active_sequence > tombstone_sequence
                    AND active_sequence <= last_sequence
                )
            );