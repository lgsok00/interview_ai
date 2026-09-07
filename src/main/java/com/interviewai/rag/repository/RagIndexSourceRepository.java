package com.interviewai.rag.repository;

import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.entity.RagIndexSource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RagIndexSourceRepository extends Repository<RagIndexSource, Long> {

    @Modifying(flushAutomatically = true)
    @Query(
            value = """
                    INSERT INTO rag_index_sources (
                        source_type,
                        source_id,
                        last_sequence,
                        lock_version
                    )
                    VALUES (:sourceType, :sourceId, 0, 0)
                    ON DUPLICATE KEY UPDATE source_id = :sourceId
                    """,
            nativeQuery = true
    )
    void ensureExists(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM RagIndexSource s
            WHERE s.sourceType = :sourceType
              AND s.sourceId = :sourceId
            """)
    Optional<RagIndexSource> findLocked(@Param("sourceType") RagSourceType sourceType, @Param("sourceId") Long sourceId);
}
