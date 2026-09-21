CREATE TABLE rag_index_sources
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    source_type   VARCHAR(30) NOT NULL,
    source_id     BIGINT      NOT NULL,
    last_sequence BIGINT      NOT NULL DEFAULT 0,
    lock_version  BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_rag_index_sources PRIMARY KEY (id),

    CONSTRAINT uk_rag_index_sources_source
        UNIQUE (source_type, source_id),

    CONSTRAINT chk_rag_index_sources_type
        CHECK (
            source_type IN (
                            'COMPANY',
                            'JOB_POSTING',
                            'COVER_LETTER',
                            'RESUME'
                )
            ),

    CONSTRAINT chk_rag_index_sources_source_id
        CHECK (source_id > 0),

    CONSTRAINT chk_rag_index_sources_sequence
        CHECK (last_sequence >= 0),

    CONSTRAINT chk_rag_index_sources_lock_version
        CHECK (lock_version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;