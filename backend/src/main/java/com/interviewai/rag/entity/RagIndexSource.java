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

import java.util.Objects;
import java.util.UUID;

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

    @Column(name = "active_generation_id", length = 36)
    private String activeGenerationId;

    @Column(name = "active_sequence", nullable = false)
    private long activeSequence;

    @Column(name = "tombstone_sequence", nullable = false)
    private long tombstoneSequence;


    protected RagIndexSource() {

    }


    public long allocateNextSequence() {
        long nextSequence = Math.incrementExact(lastSequence);
        lastSequence = nextSequence;

        return nextSequence;
    }


    public void recordDelete(long sequence) {
        if (sequence <= 0 || sequence != lastSequence) {
            throw new IllegalArgumentException("DELETE 순번은 현재 마지막 순번과 같아야 합니다.");
        }

        tombstoneSequence = sequence;
        activeGenerationId = null;
        activeSequence = 0;
    }


    public boolean activate(long sequence, UUID generationId) {
        Objects.requireNonNull(generationId, "generationId는 필수입니다.");

        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence는 양수여야 합니다.");
        }

        if (sequence != lastSequence || sequence <= tombstoneSequence) {
            return false;
        }

        activeGenerationId = generationId.toString();
        activeSequence = sequence;

        return true;
    }
}
