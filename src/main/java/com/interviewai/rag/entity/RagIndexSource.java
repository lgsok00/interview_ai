package com.interviewai.rag.entity;

import com.interviewai.rag.document.RagSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(
        name = "rag_index_sources",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_rag_index_sources_source",
                        columnNames = {"source_type", "source_id"}
                )
        }
)
public class RagIndexSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source_type", nullable = false, updatable = false, length = 30)
    private RagSourceType sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;


    protected RagIndexSource() {

    }


    public long allocateNextSequence() {
        long nextSequence = Math.incrementExact(lastSequence);
        lastSequence = nextSequence;

        return nextSequence;
    }
}
