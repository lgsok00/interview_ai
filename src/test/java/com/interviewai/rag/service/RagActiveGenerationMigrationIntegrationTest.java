package com.interviewai.rag.service;

import com.interviewai.support.MySqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class RagActiveGenerationMigrationIntegrationTest extends MySqlIntegrationTest {

    @Test
    void migratesV8DataUsingLatestDeleteRegardlessOfStatusWithoutInventingActiveGeneration() {
        var dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).target("8").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO rag_index_sources (source_type, source_id, last_sequence, lock_version)
                VALUES ('COMPANY', 1, 4, 7), ('COMPANY', 2, 1, 5),
                       ('RESUME', 1, 2, 2), ('COMPANY', 3, 0, 0)
                """);
        insertDelete(jdbc, "COMPANY", 1, "SUCCEEDED");
        insertUpsert(jdbc, 1, 2);
        insertDelete(jdbc, "COMPANY", 3, "CANCELLED");
        insertUpsert(jdbc, 1, 4);
        insertUpsert(jdbc, 2, 1);
        insertDelete(jdbc, "RESUME", 1, "PENDING");
        insertDelete(jdbc, "RESUME", 2, "FAILED");

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history WHERE version = '9' AND success = TRUE
                """, Integer.class)).isEqualTo(1);
        assertSource(jdbc, "COMPANY", 1, 4, 3, 8);
        assertSource(jdbc, "COMPANY", 2, 1, 0, 5);
        assertSource(jdbc, "RESUME", 1, 2, 2, 3);
        assertSource(jdbc, "COMPANY", 3, 0, 0, 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rag_index_jobs", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM rag_index_sources
                WHERE active_generation_id IS NULL AND active_sequence = 0
                """, Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM rag_index_jobs WHERE status = 'SUCCEEDED'
                """, Integer.class)).isEqualTo(4);
    }

    private void insertDelete(JdbcTemplate jdbc, String type, long sequence, String status) {
        jdbc.update("""
                INSERT INTO rag_index_jobs
                    (source_type, source_id, source_sequence, operation, status,
                     max_attempts, attempt_count, created_at, updated_at, lock_version)
                VALUES (?, 1, ?, 'DELETE', ?, 3, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, type, sequence, status);
    }

    private void insertUpsert(JdbcTemplate jdbc, long id, long sequence) {
        jdbc.update("""
                INSERT INTO rag_index_jobs
                    (source_type, source_id, source_sequence, operation, status,
                     company_id, snapshot_title, snapshot_content, source_revision, pipeline_version,
                     max_attempts, attempt_count, created_at, updated_at, lock_version)
                VALUES ('COMPANY', ?, ?, 'UPSERT', 'SUCCEEDED', ?, 'title', 'content', 'r1', 'rag-v1',
                        3, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, id, sequence, id);
    }

    private void assertSource(JdbcTemplate jdbc, String type, long id, long sequence, long tombstone, long version) {
        var row = jdbc.queryForMap("""
                SELECT last_sequence, tombstone_sequence, lock_version
                FROM rag_index_sources WHERE source_type = ? AND source_id = ?
                """, type, id);
        assertThat(row).containsEntry("last_sequence", sequence)
                .containsEntry("tombstone_sequence", tombstone)
                .containsEntry("lock_version", version);
    }
}
