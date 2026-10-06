CREATE TABLE ai_usage_global_state
(
    id TINYINT NOT NULL,

    CONSTRAINT pk_ai_usage_global_state PRIMARY KEY (id),
    CONSTRAINT chk_ai_usage_global_state_singleton CHECK (id = 1)
);

INSERT INTO ai_usage_global_state (id)
VALUES (1);


CREATE TABLE ai_usage_subjects
(
    subject_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at  DATETIME(6)                                    NOT NULL,

    CONSTRAINT pk_ai_usage_subjects PRIMARY KEY (subject_key)
);


CREATE TABLE ai_usage_global_daily
(
    usage_date    DATE NOT NULL,
    chat_accepted INT  NOT NULL DEFAULT 0,

    CONSTRAINT pk_ai_usage_global_daily PRIMARY KEY (usage_date),
    CONSTRAINT chk_ai_usage_global_daily_count CHECK (chat_accepted >= 0)
);


CREATE TABLE ai_usage_subject_daily
(
    subject_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL,
    usage_date  DATE                                              NOT NULL,
    feature     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    accepted    INT                                               NOT NULL DEFAULT 0,

    CONSTRAINT pk_ai_usage_subject_daily
        PRIMARY KEY (subject_key, usage_date, feature),

    CONSTRAINT fk_ai_usage_subject_daily_subject
        FOREIGN KEY (subject_key)
            REFERENCES ai_usage_subjects (subject_key),

    CONSTRAINT chk_ai_usage_subject_daily_count
        CHECK (accepted >= 0),

    CONSTRAINT chk_ai_usage_subject_daily_feature
        CHECK (feature IN (
                           'INITIAL_QUESTIONS',
                           'FOLLOW_UP',
                           'ANSWER_EVALUATION',
                           'COVER_LETTER_DRAFT',
                           'PERSONAL_EMBEDDING'
            ))
);


CREATE TABLE ai_usage_reservations
(
    id                 CHAR(36) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL,
    subject_key        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL,
    user_id            BIGINT                                            NULL,
    feature            VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_id        BIGINT                                            NULL,
    status             VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    worker_attempt_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin    NULL,
    lease_expires_at   DATETIME(6)                                       NULL,
    created_at         DATETIME(6)                                       NOT NULL,
    finished_at        DATETIME(6)                                       NULL,

    active_resource_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN resource_id END) STORED,

    CONSTRAINT pk_ai_usage_reservations PRIMARY KEY (id),

    CONSTRAINT uq_ai_usage_reservations_active_resource
        UNIQUE (feature, active_resource_id),

    CONSTRAINT fk_ai_usage_reservations_subject
        FOREIGN KEY (subject_key)
            REFERENCES ai_usage_subjects (subject_key),

    CONSTRAINT fk_ai_usage_reservations_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE SET NULL,

    CONSTRAINT chk_ai_usage_reservations_feature
        CHECK (feature IN (
                           'INITIAL_QUESTIONS',
                           'FOLLOW_UP',
                           'ANSWER_EVALUATION',
                           'COVER_LETTER_DRAFT'
            )),

    CONSTRAINT chk_ai_usage_reservations_status
        CHECK (status IN (
                          'ACTIVE',
                          'FINISHED',
                          'EXPIRED',
                          'CANCELLED'
            )),

    CONSTRAINT chk_ai_usage_reservations_resource
        CHECK (resource_id IS NULL OR resource_id > 0),

    CONSTRAINT chk_ai_usage_reservations_finished
        CHECK (
            (status = 'ACTIVE' AND finished_at IS NULL)
                OR
            (status <> 'ACTIVE' AND finished_at IS NOT NULL)
            ),

    CONSTRAINT chk_ai_usage_reservations_follow_up_lease
        CHECK (
            feature <> 'FOLLOW_UP'
                OR status <> 'ACTIVE'
                OR lease_expires_at IS NOT NULL
            ),

    INDEX idx_ai_usage_reservations_subject_active (subject_key, status),
    INDEX idx_ai_usage_reservations_global_active (status),
    INDEX idx_ai_usage_reservations_expired (feature, status, lease_expires_at),
    INDEX idx_ai_usage_reservations_user (user_id)
);


CREATE TABLE ai_usage_admin_embedding_minutes
(
    subject_key  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    window_start DATETIME(6)                                    NOT NULL,
    accepted     INT                                            NOT NULL DEFAULT 0,

    CONSTRAINT pk_ai_usage_admin_embedding_minutes
        PRIMARY KEY (subject_key, window_start),

    CONSTRAINT fk_ai_usage_admin_embedding_minutes_subject
        FOREIGN KEY (subject_key)
            REFERENCES ai_usage_subjects (subject_key),

    CONSTRAINT chk_ai_usage_admin_embedding_minutes_count
        CHECK (accepted >= 0)
);