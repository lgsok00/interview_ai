package com.interviewai.rag.metadata;

import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.index.RagIndexJob;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.index.RagIndexTarget;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Getter
public final class RagChunkMetadata {

    private final UUID pointId;
    private final Map<String, Object> metadata;


    private RagChunkMetadata(UUID pointId, Map<String, Object> metadata) {
        this.pointId = pointId;
        this.metadata = Map.copyOf(metadata);
    }


    public static RagChunkMetadata from(RagIndexJob job, UUID expectedAttemptId, int chunkIndex, int chunkCount) {
        Objects.requireNonNull(job, "job은 필수입니다.");
        job.requireRunningAttempt(expectedAttemptId);

        if (job.getOperation() != RagIndexOperation.UPSERT) {
            throw new IllegalArgumentException("UPSERT 작업만 chunk metadata를 생성할 수 있습니다.");
        }

        if (chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("chunkCount는 양수이고 chunkIndex는 0 이상 chunkCount 미만이어야 합니다.");
        }

        RagIndexTarget target = job.getTarget();
        RagSourceSnapshot snapshot = target.snapshot();
        UUID generationId = job.getId();

        String pointKey = "rag:"
                + generationId
                + ":"
                + chunkIndex;

        UUID pointId = UUID.nameUUIDFromBytes(pointKey.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", 1);
        metadata.put("sourceType", snapshot.sourceType().name());
        metadata.put("sourceId", snapshot.sourceId());
        metadata.put("visibility", snapshot.visibility().name());
        metadata.put("sourceRevision", snapshot.sourceRevision());
        metadata.put("pipelineVersion", target.pipelineVersion());
        metadata.put("generationId", generationId.toString());
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("chunkCount", chunkCount);
        metadata.put("title", snapshot.title());

        if (snapshot.ownerUserId() != null) {
            metadata.put("ownerUserId", snapshot.ownerUserId());
        }

        if (snapshot.companyId() != null) {
            metadata.put("companyId", snapshot.companyId());
        }

        if (snapshot.sourceType() == RagSourceType.JOB_POSTING) {
            metadata.put("jobPostingId", snapshot.sourceId());
        }

        return new RagChunkMetadata(pointId, metadata);
    }
}
