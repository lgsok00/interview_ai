package com.interviewai.rag.entity;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.index.RagIndexJobStatus;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.index.RagIndexTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Entity
@Getter
@Table(
        name = "rag_index_jobs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_rag_index_jobs_source_sequence",
                        columnNames = {"source_type", "source_id", "source_sequence"}
                )
        }
)
public class RagIndexJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source_type", nullable = false, updatable = false, length = 30)
    private RagSourceType sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "source_sequence", nullable = false, updatable = false)
    private long sourceSequence;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 20)
    private RagIndexOperation operation;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RagIndexJobStatus status;

    @Column(name = "owner_user_id", updatable = false)
    private Long ownerUserId;

    @Column(name = "company_id", updatable = false)
    private Long companyId;

    @Column(name = "snapshot_title", updatable = false, columnDefinition = "LONGTEXT")
    private String snapshotTitle;

    @Column(name = "snapshot_content", updatable = false, columnDefinition = "LONGTEXT")
    private String snapshotContent;

    @Column(name = "source_revision", updatable = false, columnDefinition = "LONGTEXT")
    private String sourceRevision;

    @Column(name = "pipeline_version", updatable = false, length = 100)
    private String pipelineVersion;

    @Column(name = "max_attempts", nullable = false, updatable = false)
    private int maxAttempts;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    @SuppressWarnings("FieldMayBeFinal")
    private long lockVersion = 0L;


    protected RagIndexJobEntity() {

    }


    private RagIndexJobEntity(
            RagSourceKey sourceKey,
            long sourceSequence,
            RagIndexOperation operation,
            int maxAttempts,
            Instant now
    ) {
        Objects.requireNonNull(sourceKey, "sourceKey는 필수입니다.");
        Objects.requireNonNull(operation, "operation은 필수입니다.");
        Objects.requireNonNull(now, "now는 필수입니다.");

        if (sourceSequence < 1) {
            throw new IllegalArgumentException("sourceSequence는 1 이상이어야 합니다.");
        }

        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts는 1 이상이어야 합니다.");
        }

        this.sourceType = sourceKey.sourceType();
        this.sourceId = sourceKey.sourceId();
        this.sourceSequence = sourceSequence;
        this.operation = operation;
        this.status = RagIndexJobStatus.PENDING;
        this.maxAttempts = maxAttempts;
        this.attemptCount = 0;
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = this.createdAt;
    }


    public static RagIndexJobEntity upsert(
            RagIndexTarget target,
            long sourceSequence,
            int maxAttempts,
            Instant now
    ) {
        Objects.requireNonNull(target, "target은 필수입니다.");

        RagSourceSnapshot snapshot = target.snapshot();

        RagIndexJobEntity job = new RagIndexJobEntity(
                snapshot.sourceKey(),
                sourceSequence,
                RagIndexOperation.UPSERT,
                maxAttempts,
                now
        );

        job.ownerUserId = snapshot.ownerUserId();
        job.companyId = snapshot.companyId();
        job.snapshotTitle = snapshot.title();
        job.snapshotContent = snapshot.content();
        job.sourceRevision = snapshot.sourceRevision();
        job.pipelineVersion = target.pipelineVersion();

        return job;
    }


    public static RagIndexJobEntity delete(
            RagSourceKey sourceKey,
            long sourceSequence,
            int maxAttempts,
            Instant now
    ) {
        return new RagIndexJobEntity(
                sourceKey,
                sourceSequence,
                RagIndexOperation.DELETE,
                maxAttempts,
                now
        );
    }


    @Transient
    public RagSourceKey getSourceKey() {
        return new RagSourceKey(sourceType, sourceId);
    }


    @Transient
    public RagIndexTarget getTarget() {
        if (operation == RagIndexOperation.DELETE) {
            return null;
        }

        return new RagIndexTarget(
                new RagSourceSnapshot(
                        getSourceKey(),
                        ownerUserId,
                        companyId,
                        snapshotTitle,
                        snapshotContent,
                        sourceRevision
                ),
                pipelineVersion
        );
    }
}
