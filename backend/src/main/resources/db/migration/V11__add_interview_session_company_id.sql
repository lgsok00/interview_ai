ALTER TABLE interview_sessions
    ADD COLUMN company_id BIGINT NULL AFTER user_id;

UPDATE interview_sessions interview_session
    JOIN job_postings job_posting
    ON job_posting.id = interview_session.job_posting_id
SET interview_session.company_id = job_posting.company_id
WHERE interview_session.company_id IS NULL;

ALTER TABLE interview_sessions
    MODIFY COLUMN company_id BIGINT NOT NULL;