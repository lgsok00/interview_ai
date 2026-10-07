package com.interviewai.ai.usage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class AiUsageRepository {

    private final JdbcTemplate jdbc;


    public AiUsageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


    public Optional<LockedUser> lockUser(long userId) {
        return jdbc.query("""
                        SELECT email, status
                        FROM users
                        WHERE id = ?
                        FOR UPDATE
                        """,
                (rs, rowNum) -> new LockedUser(
                        rs.getString("email"),
                        rs.getString("status")
                ),
                userId
        ).stream().findFirst();
    }


    public void lockGlobalState() {
        List<Integer> rows = jdbc.queryForList("""
                SELECT id
                FROM ai_usage_global_state
                WHERE id = 1
                FOR UPDATE
                """, Integer.class);

        if (rows.size() != 1) {
            throw AiUsageException.unavailable(
                    new IllegalStateException("AI 사용량 전역 잠금 행이 없습니다.")
            );
        }
    }


    public LocalDateTime currentUtcTime() {
        LocalDateTime now = jdbc.queryForObject(
                "SELECT UTC_TIMESTAMP(6)",
                (rs, rowNum) -> rs.getObject(1, LocalDateTime.class)
        );

        if (now == null) {
            throw AiUsageException.unavailable(
                    new IllegalStateException("DB UTC 시각을 읽을 수 없습니다.")
            );
        }

        return now;
    }


    public void lockSubject(String subjectKey, LocalDateTime now) {
        jdbc.update("""
                        INSERT INTO ai_usage_subjects (subject_key, created_at)
                        VALUES (?, ?)
                        ON DUPLICATE KEY UPDATE subject_key = subject_key
                        """,
                subjectKey,
                Timestamp.valueOf(now)
        );

        List<String> rows = jdbc.queryForList("""
                        SELECT subject_key
                        FROM ai_usage_subjects
                        WHERE subject_key = ?
                        FOR UPDATE
                        """,
                String.class,
                subjectKey
        );

        if (rows.size() != 1) {
            throw AiUsageException.unavailable(
                    new IllegalStateException("AI 사용량 주체 행이 없습니다.")
            );
        }
    }


    public boolean hasActiveReservation(
            AiUsageFeature feature,
            long resourceId
    ) {
        return !jdbc.queryForList("""
                        SELECT id
                        FROM ai_usage_reservations
                        WHERE feature = ?
                          AND active_resource_id = ?
                        FOR UPDATE
                        """,
                String.class,
                feature.name(),
                resourceId
        ).isEmpty();
    }


    public List<String> findActiveSubjectsLocked() {
        return jdbc.queryForList("""
                SELECT subject_key
                FROM ai_usage_reservations
                WHERE status = 'ACTIVE'
                FOR UPDATE
                """, String.class);
    }


    public int lockGlobalDaily(LocalDate usageDate) {
        jdbc.update("""
                        INSERT INTO ai_usage_global_daily (usage_date, chat_accepted)
                        VALUES (?, 0)
                        ON DUPLICATE KEY UPDATE usage_date = usage_date
                        """,
                java.sql.Date.valueOf(usageDate)
        );

        Integer accepted = jdbc.queryForObject("""
                        SELECT chat_accepted
                        FROM ai_usage_global_daily
                        WHERE usage_date = ?
                        FOR UPDATE
                        """,
                Integer.class,
                java.sql.Date.valueOf(usageDate)
        );

        if (accepted == null) {
            throw AiUsageException.unavailable(
                    new IllegalStateException("AI 전역 일일 사용량이 없습니다.")
            );
        }

        return accepted;
    }


    public int lockSubjectDaily(
            String subjectKey,
            LocalDate usageDate,
            AiUsageFeature feature
    ) {
        jdbc.update("""
                        INSERT INTO ai_usage_subject_daily
                            (subject_key, usage_date, feature, accepted)
                        VALUES (?, ?, ?, 0)
                        ON DUPLICATE KEY UPDATE accepted = accepted
                        """,
                subjectKey,
                java.sql.Date.valueOf(usageDate),
                feature.name()
        );

        Integer accepted = jdbc.queryForObject("""
                        SELECT accepted
                        FROM ai_usage_subject_daily
                        WHERE subject_key = ?
                          AND usage_date = ?
                          AND feature = ?
                        FOR UPDATE
                        """,
                Integer.class,
                subjectKey,
                java.sql.Date.valueOf(usageDate),
                feature.name()
        );

        if (accepted == null) {
            throw AiUsageException.unavailable(
                    new IllegalStateException("AI 주체 일일 사용량이 없습니다.")
            );
        }

        return accepted;
    }


    public void incrementGlobalDaily(LocalDate usageDate) {
        requireOne(jdbc.update("""
                        UPDATE ai_usage_global_daily
                        SET chat_accepted = chat_accepted + 1
                        WHERE usage_date = ?
                        """,
                java.sql.Date.valueOf(usageDate)
        ));
    }


    public void incrementSubjectDaily(
            String subjectKey,
            LocalDate usageDate,
            AiUsageFeature feature
    ) {
        requireOne(jdbc.update("""
                        UPDATE ai_usage_subject_daily
                        SET accepted = accepted + 1
                        WHERE subject_key = ?
                          AND usage_date = ?
                          AND feature = ?
                        """,
                subjectKey,
                java.sql.Date.valueOf(usageDate),
                feature.name()
        ));
    }


    public void insertReservation(
            UUID reservationId,
            String subjectKey,
            long userId,
            AiUsageFeature feature,
            long resourceId,
            LocalDateTime now,
            LocalDateTime leaseExpiresAt
    ) {
        String attemptId = feature == AiUsageFeature.FOLLOW_UP
                ? reservationId.toString()
                : null;

        requireOne(jdbc.update("""
                        INSERT INTO ai_usage_reservations
                        (
                            id, subject_key, user_id, feature, resource_id,
                            status, worker_attempt_id, lease_expires_at,
                            created_at, finished_at
                        )
                        VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, NULL)
                        """,
                reservationId.toString(),
                subjectKey,
                userId,
                feature.name(),
                resourceId,
                attemptId,
                leaseExpiresAt == null
                        ? null
                        : Timestamp.valueOf(leaseExpiresAt),
                Timestamp.valueOf(now)
        ));
    }


    private void requireOne(int changedRows) {
        if (changedRows != 1) {
            throw AiUsageException.unavailable(
                    new IllegalStateException("AI 사용량 변경 행 수가 올바르지 않습니다.")
            );
        }
    }


    public record LockedUser(String email, String status) {

    }
}