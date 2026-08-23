CREATE TABLE users
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NULL,
    nickname      VARCHAR(50)  NOT NULL,
    provider      VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',
    provider_id   VARCHAR(255) NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                           ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_provider_account UNIQUE (provider, provider_id),
    CONSTRAINT chk_users_provider
        CHECK (provider IN ('LOCAL', 'GOOGLE', 'GITHUB')),
    CONSTRAINT chk_users_role
        CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_authentication
        CHECK (
            (provider = 'LOCAL' AND password_hash IS NOT NULL)
                OR
            (provider <> 'LOCAL' AND provider_id IS NOT NULL)
            )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;