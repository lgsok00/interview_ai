package com.interviewai.rag.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class RagIndexJobExecutionRepository {

    private final JdbcTemplate jdbc;


    public RagIndexJobExecutionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


    public Optional<Long> findClaimableIdLocked() {
        return jdbc.queryForList("""
                SELECT id
                FROM rag_index_jobs
                WHERE (
                    status = 'PENDING'
                    AND (
                        available_at IS NULL
                        OR available_at <= UTC_TIMESTAMP(6)
                    )
                )
                OR (
                    status = 'RUNNING'
                    AND lease_expires_at <= UTC_TIMESTAMP(6)
                )
                ORDER BY id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """, Long.class).stream().findFirst();
    }


    public int failIfAttemptsExhausted(long jobId) {
        return jdbc.update("""
                UPDATE rag_index_jobs
                SET failure_code = IF(status = 'RUNNING', 'LEASE_EXPIRED', 'ATTEMPTS_EXHAUSTED'),
                    status = 'FAILED',
                    lease_expires_at = NULL,
                    available_at = NULL,
                    updated_at = GREATEST(updated_at, UTC_TIMESTAMP(6)),
                    lock_version = lock_version + 1
                WHERE id = ?
                  AND attempt_count >= max_attempts
                  AND (
                    status = 'PENDING'
                    OR (
                        status = 'RUNNING'
                        AND lease_expires_at <= UTC_TIMESTAMP(6)
                    )
                  )
                """, jobId);
    }


    public int claim(long jobId, UUID attemptId, int leaseSeconds) {
        return jdbc.update("""
                UPDATE rag_index_jobs
                SET status = 'RUNNING',
                    attempt_count = attempt_count + 1,
                    attempt_id = ?,
                    lease_expires_at = TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)),
                    available_at = NULL,
                    failure_code = NULL,
                    updated_at = GREATEST(updated_at, UTC_TIMESTAMP(6)),
                    lock_version = lock_version + 1
                WHERE id = ?
                    AND attempt_count < max_attempts
                    AND (
                      (
                          status = 'PENDING'
                          AND (
                              available_at IS NULL
                              OR available_at <= UTC_TIMESTAMP(6)
                          )
                      )
                      OR (
                          status = 'RUNNING'
                          AND lease_expires_at <= UTC_TIMESTAMP(6)
                      )
                    )
                """, attemptId.toString(), leaseSeconds, jobId);
    }


    public int renew(long jobId, UUID attemptId, int leaseSeconds) {
        return jdbc.update("""
                UPDATE rag_index_jobs
                SET lease_expires_at = GREATEST(
                        lease_expires_at,
                        TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6))
                    ),
                    updated_at = GREATEST(updated_at, UTC_TIMESTAMP(6)),
                    lock_version = lock_version + 1
                WHERE id = ?
                    AND status = 'RUNNING'
                    AND attempt_id = ?
                    AND lease_expires_at > UTC_TIMESTAMP(6)
                """, leaseSeconds, jobId, attemptId.toString());
    }


    public int succeed(long jobId, UUID attemptId) {
        return jdbc.update("""
                UPDATE rag_index_jobs
                SET status = 'SUCCEEDED',
                    lease_expires_at = NULL,
                    available_at = NULL,
                    failure_code = NULL,
                    updated_at = GREATEST(updated_at, UTC_TIMESTAMP(6)),
                    lock_version = lock_version + 1
                WHERE id = ?
                    AND status = 'RUNNING'
                    AND attempt_id = ?
                    AND lease_expires_at > UTC_TIMESTAMP(6)
                """, jobId, attemptId.toString());
    }


    public int fail(long jobId, UUID attemptId, String failureCode, int retryDelaySeconds) {
        return jdbc.update("""
                UPDATE rag_index_jobs
                SET status = IF(attempt_count < max_attempts, 'PENDING', 'FAILED'),
                    lease_expires_at = NULL,
                    available_at = IF(attempt_count < max_attempts, TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)), NULL),
                    failure_code = ?,
                    updated_at = GREATEST(updated_at, UTC_TIMESTAMP(6)),
                    lock_version = lock_version + 1
                WHERE id = ?
                    AND status = 'RUNNING'
                    AND attempt_id = ?
                    AND lease_expires_at > UTC_TIMESTAMP(6)
                """, retryDelaySeconds, failureCode, jobId, attemptId.toString());
    }
}
