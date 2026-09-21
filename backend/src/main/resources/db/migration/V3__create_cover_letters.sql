CREATE TABLE cover_letters
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                BIGINT       NOT NULL,
    title                  VARCHAR(100) NOT NULL,
    current_version_number INT          NOT NULL,
    created_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cover_letters PRIMARY KEY (id),
    CONSTRAINT uk_cover_letters_user_id_id UNIQUE (user_id, id),
    CONSTRAINT fk_cover_letters_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT chk_cover_letters_current_version
        CHECK (current_version_number >= 1),

    INDEX idx_cover_letters_user_updated_at (user_id, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE cover_letter_versions
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    cover_letter_id BIGINT       NOT NULL,
    version_number  INT          NOT NULL,
    title           VARCHAR(100) NOT NULL,
    content         MEDIUMTEXT   NOT NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cover_letter_versions PRIMARY KEY (id),
    CONSTRAINT uk_cover_letter_versions_letter_version
        UNIQUE (cover_letter_id, version_number),
    CONSTRAINT fk_cover_letter_versions_cover_letter
        FOREIGN KEY (cover_letter_id) REFERENCES cover_letters (id)
            ON DELETE CASCADE,
    CONSTRAINT chk_cover_letter_versions_version
        CHECK (version_number >= 1),

    INDEX idx_cover_letter_versions_letter_created_at
        (cover_letter_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE cover_letter_representatives
(
    user_id         BIGINT      NOT NULL,
    cover_letter_id BIGINT      NOT NULL,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_cover_letter_representatives PRIMARY KEY (user_id),
    CONSTRAINT uk_cover_letter_representatives_cover_letter
        UNIQUE (cover_letter_id),
    CONSTRAINT fk_cover_letter_representatives_owned_cover_letter
        FOREIGN KEY (user_id, cover_letter_id)
            REFERENCES cover_letters (user_id, id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;