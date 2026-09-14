package com.interviewai.interview.generation;

import com.interviewai.global.error.CatalogException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class InterviewGenerationExecutionService {

    private final JdbcTemplate jdbc;
    private final InterviewGenerationProperties properties;


    public InterviewGenerationExecutionService(JdbcTemplate jdbc, InterviewGenerationProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void register(long sessionId) {
        jdbc.update("""
                        INSERT INTO interview_generation_jobs
                        (
                            session_id, status, generation_mode, model_name,
                            pipeline_version, available_at, created_at, updated_at
                        )
                        VALUES (?, 'PENDING', ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                        """,
                sessionId,
                properties.mode().name(),
                properties.model().isBlank() ? null : properties.model(),
                InterviewGenerationPolicy.VERSION
        );
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Claim> claimNext() {
        List<Long> candidates = jdbc.queryForList("""
                SELECT job.session_id
                FROM interview_generation_jobs job
                JOIN interview_sessions session ON session.id = job.session_id
                WHERE session.status = 'GENERATING'
                  AND (
                      (job.status = 'PENDING'
                          AND (job.available_at IS NULL
                              OR job.available_at <= UTC_TIMESTAMP(6)))
                      OR
                      (job.status = 'RUNNING'
                          AND job.lease_expires_at <= UTC_TIMESTAMP(6))
                  )
                ORDER BY job.session_id
                LIMIT 20
                """, Long.class);

        for (Long sessionId : candidates) {
            List<Long> locked = jdbc.queryForList("""
                    SELECT id
                    FROM interview_sessions
                    WHERE id = ? AND status = 'GENERATING'
                    FOR UPDATE SKIP LOCKED
                    """, Long.class, sessionId);

            if (locked.isEmpty()) {
                continue;
            }

            List<Job> jobs = jdbc.query("""
                            SELECT generation_mode, model_name, pipeline_version, attempt_count
                            FROM interview_generation_jobs
                            WHERE session_id = ?
                              AND (
                                (status = 'PENDING'
                                    AND (available_at IS NULL
                                        OR available_at <= UTC_TIMESTAMP(6)))
                                OR
                                (status = 'RUNNING'
                                    AND lease_expires_at <= UTC_TIMESTAMP(6))
                              )
                            FOR UPDATE
                            """,
                    (rs, rowNum) -> new Job(
                            rs.getString("generation_mode"),
                            rs.getString("model_name"),
                            rs.getString("pipeline_version"),
                            rs.getInt("attempt_count")
                    ),
                    sessionId
            );

            if (jobs.isEmpty()) {
                continue;
            }

            Job job = jobs.getFirst();
            boolean recoveryOnly = job.attemptCount() >= InterviewGenerationPolicy.MAX_ATTEMPTS;

            int attemptCount = recoveryOnly ? job.attemptCount() : job.attemptCount() + 1;

            String attemptId = UUID.randomUUID().toString();

            jdbc.update("""
                            UPDATE interview_generation_jobs
                            SET status = 'RUNNING',
                                attempt_count = ?,
                                attempt_id = ?,
                                lease_expires_at = TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)),
                                available_at = NULL,
                                updated_at = UTC_TIMESTAMP(6)
                            WHERE session_id = ?
                            """,
                    attemptCount,
                    attemptId,
                    InterviewGenerationPolicy.LEASE_SECONDS,
                    sessionId
            );

            return Optional.of(new Claim(
                    sessionId,
                    attemptId,
                    attemptCount,
                    InterviewGenerationPolicy.Mode.valueOf(job.mode()),
                    job.model(),
                    job.version(),
                    recoveryOnly
            ));
        }

        return Optional.empty();
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renew(Claim claim) {
        if (!lockGeneratingSession(claim.sessionId)) {
            return false;
        }

        return jdbc.update("""
                        UPDATE interview_generation_jobs
                        SET lease_expires_at = TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)),
                            updated_at = UTC_TIMESTAMP(6)
                        WHERE session_id = ?
                          AND status = 'RUNNING'
                          AND attempt_id = ?
                          AND lease_expires_at > UTC_TIMESTAMP(6)
                        """,
                InterviewGenerationPolicy.LEASE_SECONDS,
                claim.sessionId(),
                claim.attemptId()
        ) == 1;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(Claim claim, InterviewGenerationPolicy.Batch batch) {
        if (!lockGeneratingSession(claim.sessionId())) {
            return false;
        }

        int accepted = jdbc.update("""
                        UPDATE interview_generation_jobs
                        SET status = 'SUCCEEDED',
                            lease_expires_at = NULL,
                            available_at = NULL,
                            fallback_reason = ?,
                            updated_at = UTC_TIMESTAMP(6)
                        WHERE session_id = ?
                          AND status = 'RUNNING'
                          AND attempt_id = ?
                          AND lease_expires_at > UTC_TIMESTAMP(6)
                        """,
                batch.fallbackReason(),
                claim.sessionId(),
                claim.attemptId()
        );

        if (accepted == 0) {
            return false;
        }

        if (hasQuestions(claim.sessionId())) {
            throw new IllegalStateException("GENERATING 세션에 이미 질문이 존재합니다: " + claim.sessionId());
        }

        int sequence = 1;

        for (InterviewGenerationPolicy.Question question : batch.questions()) {
            jdbc.update("""
                            INSERT INTO interview_questions
                            (
                                session_id, sequence_number, question_type,
                                generation_source, content, context_snapshot, created_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6))
                            """,
                    claim.sessionId(),
                    sequence++,
                    question.type().name(),
                    batch.source().name(),
                    question.content(),
                    batch.contextSnapshot()
            );
        }

        jdbc.update("""
                UPDATE interview_sessions
                SET status = 'READY',
                    failure_code = NULL,
                    updated_at = UTC_TIMESTAMP(6)
                WHERE id = ?
                """, claim.sessionId());

        return true;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(Claim claim, String code, boolean retryable) {
        requireCode(code);

        if (!lockGeneratingSession(claim.sessionId())) {
            return false;
        }

        boolean retry = retryable && claim.attemptCount() < InterviewGenerationPolicy.MAX_ATTEMPTS;

        int delay = (claim.attemptCount() == 1 ? 5 : 20) + ThreadLocalRandom.current().nextInt(3);

        int accepted;

        if (retry) {
            accepted = jdbc.update("""
                            UPDATE interview_generation_jobs
                            SET status = 'PENDING',
                                lease_expires_at = NULL,
                                available_at = TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)),
                                last_error_code = ?,
                                updated_at = UTC_TIMESTAMP(6)
                            WHERE session_id = ?
                              AND status = 'RUNNING'
                              AND attempt_id = ?
                              AND lease_expires_at > UTC_TIMESTAMP(6)
                            """,
                    delay, code, claim.sessionId(), claim.attemptId()
            );

        } else {
            accepted = jdbc.update("""
                            UPDATE interview_generation_jobs
                            SET status = 'FAILED',
                                lease_expires_at = NULL,
                                available_at = NULL,
                                last_error_code = ?,
                                updated_at = UTC_TIMESTAMP(6)
                            WHERE session_id = ?
                              AND status = 'RUNNING'
                              AND attempt_id = ?
                              AND lease_expires_at > UTC_TIMESTAMP(6)
                            """,
                    code, claim.sessionId(), claim.attemptId()
            );
        }

        if (accepted == 1 && !retry) {
            jdbc.update("""
                    UPDATE interview_sessions
                    SET status = 'FAILED',
                        failure_code = ?,
                        updated_at = UTC_TIMESTAMP(6)
                    WHERE id = ?
                    """, code, claim.sessionId());
        }

        return accepted == 1;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(long userId, long sessionId) {
        List<String> statuses = jdbc.queryForList("""
                SELECT status
                FROM interview_sessions
                WHERE id = ? AND user_id = ?
                FOR UPDATE
                """, String.class, sessionId, userId);

        if (statuses.isEmpty()) {
            throw new CatalogException(HttpStatus.NOT_FOUND, "INTERVIEW_SESSION_NOT_FOUND", "면접 세션을 찾을 수 없습니다.");
        }

        if (!"FAILED".equals(statuses.getFirst())) {
            throw conflict("실패한 면접 세션만 재시도할 수 있습니다.");
        }

        if (hasQuestions(sessionId)) {
            throw conflict("이미 질문이 저장된 세션은 재시도할 수 없습니다.");
        }

        int updated = jdbc.update("""
                UPDATE interview_generation_jobs
                SET status = 'PENDING',
                    attempt_count = 0,
                    retry_count = retry_count + 1,
                    attempt_id = NULL,
                    lease_expires_at = NULL,
                    available_at = UTC_TIMESTAMP(6),
                    last_error_code = NULL,
                    fallback_reason = NULL,
                    updated_at = UTC_TIMESTAMP(6)
                WHERE session_id = ?
                  AND status = 'FAILED'
                  AND retry_count < ?
                """, sessionId, InterviewGenerationPolicy.MAX_MANUAL_RETRIES);

        if (updated == 0) {
            throw conflict("재시도 한도를 초과했거나 재시도 가능한 작업이 없습니다.");
        }

        jdbc.update("""
                UPDATE interview_sessions
                SET status = 'GENERATING',
                    failure_code = NULL,
                    updated_at = UTC_TIMESTAMP(6)
                WHERE id = ?
                """, sessionId);
    }


    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean lockGeneratingSession(long sessionId) {
        List<String> statuses = jdbc.queryForList("""
                SELECT status
                FROM interview_sessions
                WHERE id = ?
                FOR UPDATE
                """, String.class, sessionId);

        return !statuses.isEmpty() && "GENERATING".equals(statuses.getFirst());
    }


    private boolean hasQuestions(long sessionId) {
        return !jdbc.queryForList("""
                SELECT id
                FROM interview_questions
                WHERE session_id = ?
                LIMIT 1
                FOR UPDATE
                """, Long.class, sessionId).isEmpty();
    }


    private void requireCode(String code) {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,49}")) {
            throw new IllegalArgumentException("실패 코드 형식이 올바르지 않습니다.");
        }
    }


    private CatalogException conflict(String message) {
        return new CatalogException(HttpStatus.CONFLICT, "INTERVIEW_GENERATION_CONFLICT", message);
    }


    public record Claim(
            long sessionId,
            String attemptId,
            int attemptCount,
            InterviewGenerationPolicy.Mode mode,
            String model,
            String version,
            boolean recoveryOnly
    ) {

    }


    private record Job(
            String mode,
            String model,
            String version,
            int attemptCount
    ) {

    }
}
