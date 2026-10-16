package com.interviewai.ai.usage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class AiUsageQueryRepository {

    private final JdbcTemplate jdbc;


    public AiUsageQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


    public LocalDateTime currentUtcTime() {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT UTC_TIMESTAMP(6)",
                        (rs, rowNum) -> rs.getObject(1, LocalDateTime.class)
                ),
                "DB UTC 시각을 읽을 수 없습니다."
        );
    }


    public Snapshot findSnapshot(String subjectKey, LocalDate usageDate) {
        return Objects.requireNonNull(
                jdbc.queryForObject("""
                                SELECT
                                    COALESCE(SUM(IF(feature = 'INITIAL_QUESTIONS', accepted, 0)), 0) AS initial_used,
                                    COALESCE(SUM(IF(feature = 'FOLLOW_UP', accepted, 0)), 0) AS follow_up_used,
                                    COALESCE(SUM(IF(feature = 'ANSWER_EVALUATION', accepted, 0)), 0) AS evaluation_used,
                                    COALESCE(SUM(IF(feature = 'COVER_LETTER_DRAFT', accepted, 0)), 0) AS draft_used,
                                    (
                                        SELECT COUNT(*)
                                        FROM ai_usage_reservations
                                        WHERE subject_key = ?
                                          AND status = 'ACTIVE'
                                    ) AS active_count
                                FROM ai_usage_subject_daily
                                WHERE subject_key = ?
                                  AND usage_date = ?
                                """,
                        (rs, rowNum) -> new Snapshot(
                                Map.of(
                                        AiUsageFeature.INITIAL_QUESTIONS,
                                        rs.getInt("initial_used"),
                                        AiUsageFeature.FOLLOW_UP,
                                        rs.getInt("follow_up_used"),
                                        AiUsageFeature.ANSWER_EVALUATION,
                                        rs.getInt("evaluation_used"),
                                        AiUsageFeature.COVER_LETTER_DRAFT,
                                        rs.getInt("draft_used")
                                ),
                                rs.getLong("active_count")
                        ),
                        subjectKey,
                        subjectKey,
                        Date.valueOf(usageDate)
                ),
                "AI 사용량을 읽을 수 없습니다."
        );
    }


    public record Snapshot(Map<AiUsageFeature, Integer> used, long active) {

    }
}
