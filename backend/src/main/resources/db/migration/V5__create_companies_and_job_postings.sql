CREATE TABLE companies
(
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)  NOT NULL,
    industry    VARCHAR(100)  NULL,
    description MEDIUMTEXT    NOT NULL,
    website_url VARCHAR(2048) NULL,
    location    VARCHAR(200)  NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,

    CONSTRAINT pk_companies PRIMARY KEY (id),
    CONSTRAINT chk_companies_name
        CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_companies_description
        CHECK (
            CHAR_LENGTH(TRIM(description)) > 0
                AND CHAR_LENGTH(description) <= 20000
            ),

    INDEX idx_companies_created_id (created_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE job_postings
(
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    company_id      BIGINT        NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    job_role        VARCHAR(100)  NOT NULL,
    employment_type VARCHAR(20)   NOT NULL,
    location        VARCHAR(200)  NULL,
    description     MEDIUMTEXT    NOT NULL,
    source_url      VARCHAR(2048) NULL,
    opens_at        DATETIME(6)   NULL,
    closes_at       DATETIME(6)   NULL,
    manually_closed BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,

    CONSTRAINT pk_job_postings PRIMARY KEY (id),
    CONSTRAINT fk_job_postings_company
        FOREIGN KEY (company_id) REFERENCES companies (id)
            ON DELETE RESTRICT,
    CONSTRAINT chk_job_postings_title
        CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT chk_job_postings_job_role
        CHECK (CHAR_LENGTH(TRIM(job_role)) > 0),
    CONSTRAINT chk_job_postings_description
        CHECK (
            CHAR_LENGTH(TRIM(description)) > 0
                AND CHAR_LENGTH(description) <= 30000
            ),
    CONSTRAINT chk_job_postings_employment_type
        CHECK (
            employment_type IN (
                                'FULL_TIME',
                                'CONTRACT',
                                'INTERN',
                                'PART_TIME',
                                'OTHER'
                )
            ),
    CONSTRAINT chk_job_postings_period
        CHECK (
            opens_at IS NULL
                OR closes_at IS NULL
                OR opens_at < closes_at
            ),
    CONSTRAINT chk_job_postings_manually_closed
        CHECK (manually_closed IN (0, 1)),

    INDEX idx_job_postings_company_created_id (company_id, created_at, id),
    INDEX idx_job_postings_created_id (created_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE company_favorites
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    company_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_company_favorites PRIMARY KEY (id),
    CONSTRAINT uk_company_favorites_user_company
        UNIQUE (user_id, company_id),
    CONSTRAINT fk_company_favorites_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_company_favorites_company
        FOREIGN KEY (company_id) REFERENCES companies (id)
            ON DELETE CASCADE,

    INDEX idx_company_favorites_user_created_id (user_id, created_at, id),
    INDEX idx_company_favorites_company (company_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;