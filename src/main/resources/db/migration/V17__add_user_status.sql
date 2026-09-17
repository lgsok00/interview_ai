ALTER TABLE users
    ADD COLUMN status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER role,
    ADD COLUMN suspended_at DATETIME(6) NULL AFTER status,
    ADD CONSTRAINT chk_users_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    ADD CONSTRAINT chk_users_suspension
        CHECK (
            (status = 'ACTIVE' AND suspended_at IS NULL)
                OR
            (status = 'SUSPENDED' AND suspended_at IS NOT NULL)
            );

CREATE INDEX idx_users_status_role_id
    ON users (status, role, id);