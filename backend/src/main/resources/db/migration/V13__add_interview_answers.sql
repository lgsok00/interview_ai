CREATE TABLE interview_answers
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    question_id BIGINT      NOT NULL,
    content     MEDIUMTEXT  NOT NULL,
    created_at  DATETIME(6) NOT NULL,

    CONSTRAINT pk_interview_answers PRIMARY KEY (id),

    CONSTRAINT uk_interview_answers_question
        UNIQUE (question_id),

    CONSTRAINT fk_interview_answers_question
        FOREIGN KEY (question_id) REFERENCES interview_questions (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_interview_answers_content_length
        CHECK (CHAR_LENGTH(content) BETWEEN 1 AND 10000)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


ALTER TABLE interview_questions
    ADD COLUMN parent_question_id BIGINT NULL,
    ADD CONSTRAINT uk_interview_questions_parent
        UNIQUE (parent_question_id),
    ADD CONSTRAINT fk_interview_questions_parent
        FOREIGN KEY (parent_question_id) REFERENCES interview_questions (id)
            ON DELETE CASCADE;