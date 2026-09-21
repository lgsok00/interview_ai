ALTER TABLE external_collection_requests
    DROP CHECK chk_external_collection_requests_counts;

ALTER TABLE external_collection_requests
    ADD CONSTRAINT chk_external_collection_requests_counts
        CHECK (
            attempt_count BETWEEN 0 AND 9
                AND manual_retry_count BETWEEN 0 AND 2
            );

ALTER TABLE external_collection_snapshots
    DROP CHECK chk_external_collection_snapshots_attempt_number;

ALTER TABLE external_collection_snapshots
    ADD CONSTRAINT chk_external_collection_snapshots_attempt_number
        CHECK (attempt_number BETWEEN 1 AND 9);