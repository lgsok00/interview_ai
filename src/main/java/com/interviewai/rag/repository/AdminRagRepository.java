package com.interviewai.rag.repository;

import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.dto.AdminRagResponse;
import com.interviewai.rag.index.RagIndexJobStatus;
import com.interviewai.rag.index.RagIndexOperation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class AdminRagRepository {

    private static final String SOURCE_COLUMNS = """
            SELECT id, source_type, source_id, last_sequence,
                   active_generation_id, active_sequence, tombstone_sequence
            """;

    private static final String JOB_COLUMNS = """
            SELECT id, source_type, source_id, source_sequence,
                   operation, status, attempt_count, max_attempts,
                   manual_retry_count, failure_code, available_at,
                   lease_expires_at, created_at, updated_at
            """;

    private static final String SOURCE_FILTER = """
            FROM rag_index_sources
            WHERE (:sourceType IS NULL OR source_type = :sourceType)
              AND (:sourceId IS NULL OR source_id = :sourceId)
            """;

    private static final String JOB_FILTER = """
            FROM rag_index_jobs
            WHERE (:sourceType IS NULL OR source_type = :sourceType)
              AND (:sourceId IS NULL OR source_id = :sourceId)
              AND (:status IS NULL OR status = :status)
              AND (:operation IS NULL OR operation = :operation)
            """;

    private static final RowMapper<AdminRagResponse.Source> SOURCE_MAPPER =
            (rs, rowNum) -> new AdminRagResponse.Source(
                    rs.getLong("id"),
                    RagSourceType.valueOf(rs.getString("source_type")),
                    rs.getLong("source_id"),
                    rs.getLong("last_sequence"),
                    rs.getString("active_generation_id"),
                    rs.getLong("active_sequence"),
                    rs.getLong("tombstone_sequence")
            );

    private static final RowMapper<AdminRagResponse.Job> JOB_MAPPER =
            (rs, rowNum) -> new AdminRagResponse.Job(
                    rs.getLong("id"),
                    RagSourceType.valueOf(rs.getString("source_type")),
                    rs.getLong("source_id"),
                    rs.getLong("source_sequence"),
                    RagIndexOperation.valueOf(rs.getString("operation")),
                    RagIndexJobStatus.valueOf(rs.getString("status")),
                    rs.getInt("attempt_count"),
                    rs.getInt("max_attempts"),
                    rs.getInt("manual_retry_count"),
                    rs.getString("failure_code"),
                    instant(rs, "available_at"),
                    instant(rs, "lease_expires_at"),
                    instant(rs, "created_at"),
                    instant(rs, "updated_at")
            );

    private final NamedParameterJdbcTemplate jdbc;


    public AdminRagRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


    private static MapSqlParameterSource sourceParams(RagSourceType sourceType, Long sourceId) {
        return new MapSqlParameterSource("sourceType", sourceType == null ? null : sourceType.name())
                .addValue("sourceId", sourceId);
    }


    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);

        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }


    public AdminRagResponse.Page<AdminRagResponse.Source> findSources(
            RagSourceType sourceType,
            Long sourceId,
            int page,
            int size
    ) {
        MapSqlParameterSource params = sourceParams(sourceType, sourceId)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) " + SOURCE_FILTER,
                params,
                Long.class
        );

        List<AdminRagResponse.Source> items = jdbc.query(
                SOURCE_COLUMNS + SOURCE_FILTER
                        + " ORDER BY id DESC LIMIT :limit OFFSET :offset",
                params,
                SOURCE_MAPPER
        );

        return AdminRagResponse.Page.of(items, page, size, count == null ? 0 : count);
    }


    public Optional<AdminRagResponse.Source> findSource(RagSourceType sourceType, long sourceId) {
        return jdbc
                .query(
                        SOURCE_COLUMNS + """
                                FROM rag_index_sources
                                WHERE source_type = :sourceType
                                  AND source_id = :sourceId
                                """,
                        sourceParams(sourceType, sourceId),
                        SOURCE_MAPPER
                )
                .stream()
                .findFirst();
    }


    public AdminRagResponse.Page<AdminRagResponse.Job> findJobs(
            RagSourceType sourceType,
            Long sourceId,
            RagIndexJobStatus status,
            RagIndexOperation operation,
            int page,
            int size
    ) {
        MapSqlParameterSource params = sourceParams(sourceType, sourceId)
                .addValue("status", status == null ? null : status.name())
                .addValue("operation", operation == null ? null : operation.name())
                .addValue("limit", size)
                .addValue("offset", (long) page * size);

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) " + JOB_FILTER,
                params,
                Long.class
        );

        List<AdminRagResponse.Job> items = jdbc.query(
                JOB_COLUMNS + JOB_FILTER
                        + "ORDER BY created_at DESC, id DESC"
                        + " LIMIT :limit OFFSET :offset",
                params,
                JOB_MAPPER
        );

        return AdminRagResponse.Page.of(items, page, size, count == null ? 0 : count);
    }


    public Optional<AdminRagResponse.Job> findJob(Long jobId) {
        return jdbc
                .query(
                        JOB_COLUMNS + " FROM rag_index_jobs WHERE id = :jobId",
                        new MapSqlParameterSource("jobId", jobId),
                        JOB_MAPPER
                )
                .stream()
                .findFirst();
    }


    public int retryFailed(long jobId, int additionalAttempts) {
        return jdbc.update("""
                        UPDATE rag_index_jobs
                        SET status = 'PENDING',
                            max_attempts = max_attempts + :additionalAttempts,
                            manual_retry_count = manual_retry_count + 1,
                            attempt_id = NULL,
                            lease_expires_at = NULL,
                            available_at = UTC_TIMESTAMP(6),
                            failure_code = NULL,
                            updated_at = GREATEST(updated_at, UTC_TIMESTAMP(6)),
                            lock_version = lock_version + 1
                        WHERE id = :jobId
                          AND status = 'FAILED'
                          AND manual_retry_count < 2
                          AND max_attempts <= 2147483647 - :additionalAttempts
                        """,
                new MapSqlParameterSource("jobId", jobId)
                        .addValue("additionalAttempts", additionalAttempts)
        );
    }
}
