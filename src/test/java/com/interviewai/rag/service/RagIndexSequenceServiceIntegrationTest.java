package com.interviewai.rag.service;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.support.MySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(RagIndexSequenceService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RagIndexSequenceServiceIntegrationTest extends MySqlIntegrationTest {

    private static final RagSourceKey KEY = new RagSourceKey(RagSourceType.COMPANY, 10L);

    @Autowired RagIndexSequenceService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearSources() {
        jdbc.update("""
                DELETE FROM rag_index_sources
                WHERE (source_type = ? AND source_id IN (?, ?))
                   OR (source_type = ? AND source_id = ?)
                """, KEY.sourceType().name(), KEY.sourceId(), 11L,
                RagSourceType.RESUME.name(), KEY.sourceId());
    }

    @Test
    @DisplayName("Flyway V6 migration이 적용된다")
    void appliesMigration() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = TRUE",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("원본 없이 관리 행을 생성하고 별도 트랜잭션에서도 순번과 버전을 유지한다")
    void persistsSequenceAcrossTransactions() {
        assertThat(allocate(KEY)).isEqualTo(1L);
        assertThat(sequence(KEY)).isEqualTo(1L);
        long firstVersion = version();
        assertThat(allocate(KEY)).isEqualTo(2L);
        assertThat(sequence(KEY)).isEqualTo(2L);
        assertThat(version()).isGreaterThan(firstVersion);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 트랜잭션의 연속 호출은 순번을 초기화하거나 갱신을 잃지 않는다")
    void allocatesTwiceInOneTransaction() {
        List<Long> values = transaction().execute(status ->
                List.of(service.allocateNext(KEY), service.allocateNext(KEY)));
        assertThat(values).containsExactly(1L, 2L);
        assertThat(sequence(KEY)).isEqualTo(2L);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 ID의 다른 유형과 같은 유형의 다른 ID는 독립적인 순번을 갖는다")
    void separatesSourceKeys() {
        RagSourceKey otherType = new RagSourceKey(RagSourceType.RESUME, KEY.sourceId());
        RagSourceKey otherId = new RagSourceKey(RagSourceType.COMPANY, 11L);
        assertThat(allocate(KEY)).isEqualTo(1L);
        assertThat(allocate(otherType)).isEqualTo(1L);
        assertThat(allocate(otherId)).isEqualTo(1L);
        assertThat(allocate(KEY)).isEqualTo(2L);
        assertThat(sequence(otherType)).isEqualTo(1L);
        assertThat(sequence(otherId)).isEqualTo(1L);
        assertThat(count()).isEqualTo(3);
    }

    @Test
    @DisplayName("호출자 트랜잭션이 없으면 관리 행을 만들지 않고 거부한다")
    void requiresCallerTransaction() {
        assertThatThrownBy(() -> service.allocateNext(KEY))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("null 원본 키는 DB 변경 없이 거부한다")
    void rejectsNullKey() {
        assertThatNullPointerException().isThrownBy(() ->
                transaction().execute(status -> service.allocateNext(null)));
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("신규 순번 발급 후 후속 작업이 실패하면 관리 행도 롤백한다")
    void rollsBackNewSource() {
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            service.allocateNext(KEY);
            throw new IllegalStateException("후속 작업 저장 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("후속 작업 저장 실패");
        assertThat(count()).isZero();
        assertThat(allocate(KEY)).isEqualTo(1L);
    }

    @Test
    @DisplayName("기존 순번을 flush한 뒤 롤백하면 순번과 버전이 복구된다")
    void rollsBackExistingSource() {
        allocate(KEY);
        long originalVersion = version();
        transaction().executeWithoutResult(status -> {
            assertThat(service.allocateNext(KEY)).isEqualTo(2L);
            // 두 번째 호출의 native query가 앞선 변경을 flush한다.
            assertThat(service.allocateNext(KEY)).isEqualTo(3L);
            status.setRollbackOnly();
        });
        assertThat(sequence(KEY)).isEqualTo(1L);
        assertThat(version()).isEqualTo(originalVersion);
        assertThat(allocate(KEY)).isEqualTo(2L);
    }

    @Test
    @DisplayName("최대 순번까지 발급하고 overflow 시 DB 순번과 버전을 유지한다")
    void rejectsOverflow() {
        allocate(KEY);
        assertThat(jdbc.update("""
                UPDATE rag_index_sources SET last_sequence = ?
                WHERE source_type = ? AND source_id = ?
                """, Long.MAX_VALUE - 1, KEY.sourceType().name(), KEY.sourceId()))
                .isEqualTo(1);
        assertThat(allocate(KEY)).isEqualTo(Long.MAX_VALUE);
        long originalVersion = version();
        assertThatThrownBy(() -> allocate(KEY)).isInstanceOf(ArithmeticException.class);
        assertThat(sequence(KEY)).isEqualTo(Long.MAX_VALUE);
        assertThat(version()).isEqualTo(originalVersion);
    }

    @Test
    @DisplayName("없는 원본에 독립 트랜잭션이 동시에 등록해도 행 하나와 순번 1·2를 저장한다")
    void serializesFirstCreation() throws Exception {
        assertThat(race()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(count()).isEqualTo(1);
        assertThat(sequence(KEY)).isEqualTo(2L);
    }

    @Test
    @DisplayName("기존 원본에 동시 등록하면 순번 중복과 갱신 유실 없이 저장한다")
    void serializesExistingSource() throws Exception {
        allocate(KEY);
        assertThat(race()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(count()).isEqualTo(1);
        assertThat(sequence(KEY)).isEqualTo(3L);
    }

    @ParameterizedTest
    @CsvSource({
            "UNKNOWN, 1, 0, 0, chk_rag_index_sources_type",
            "COMPANY, 0, 0, 0, chk_rag_index_sources_source_id",
            "COMPANY, 1, -1, 0, chk_rag_index_sources_sequence",
            "COMPANY, 1, 0, -1, chk_rag_index_sources_lock_version"
    })
    @DisplayName("MySQL CHECK 제약이 잘못된 원본 및 음수 순번·버전을 거부한다")
    void enforcesChecks(String type, long id, long sequence, long version, String constraint) {
        Throwable failure = catchThrowable(() -> jdbc.update("""
                INSERT INTO rag_index_sources (source_type, source_id, last_sequence, lock_version)
                VALUES (?, ?, ?, ?)
                """, type, id, sequence, version));
        assertThat(failure).isInstanceOf(DataAccessException.class);
        assertThat(failure).rootCause().isInstanceOfSatisfying(SQLException.class, sql -> {
            assertThat(sql.getErrorCode()).isEqualTo(3819);
            assertThat(sql.getSQLState()).isEqualTo("HY000");
            assertThat(sql.getMessage()).contains(constraint);
        });
        assertThat(count()).isZero();
    }

    private List<Long> race() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Long> task = () -> transaction().execute(status -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return service.allocateNext(KEY);
            });
            var first = executor.submit(task);
            var second = executor.submit(task);
            try {
                return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(15);
        return transaction;
    }

    private Long allocate(RagSourceKey key) {
        return transaction().execute(status -> service.allocateNext(key));
    }

    private Long sequence(RagSourceKey key) {
        return jdbc.queryForObject("""
                SELECT last_sequence FROM rag_index_sources
                WHERE source_type = ? AND source_id = ?
                """, Long.class, key.sourceType().name(), key.sourceId());
    }

    private Long version() {
        return jdbc.queryForObject("""
                SELECT lock_version FROM rag_index_sources
                WHERE source_type = ? AND source_id = ?
                """, Long.class, KEY.sourceType().name(), KEY.sourceId());
    }

    private Integer count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM rag_index_sources", Integer.class);
    }
}
