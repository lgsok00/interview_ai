ALTER TABLE interview_sessions
    ADD COLUMN completed_at DATETIME(6) NULL AFTER failure_code;

UPDATE interview_sessions
SET completed_at = updated_at
WHERE status = 'COMPLETED'
  AND completed_at IS NULL;

ALTER TABLE interview_sessions
    ADD CONSTRAINT chk_interview_sessions_completed_at
        CHECK (
            (status = 'COMPLETED' AND completed_at IS NOT NULL)
                OR
            (status <> 'COMPLETED' AND completed_at IS NULL)
            ),
    ADD INDEX idx_interview_sessions_growth_analysis
        (user_id, status, completed_at);