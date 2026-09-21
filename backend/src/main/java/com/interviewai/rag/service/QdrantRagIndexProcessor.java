package com.interviewai.rag.service;

import com.interviewai.rag.config.RagIndexingProperties;
import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.index.RagIndexTarget;
import com.interviewai.rag.service.RagIndexJobExecutionService.ClaimedJob;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

@Service
@ConditionalOnProperty(
        prefix = "rag.indexing",
        name = "enabled",
        havingValue = "true"
)
public class QdrantRagIndexProcessor implements RagIndexJobWorker.Processor {

    private final VectorStore vectorStore;
    private final RagTokenChunker chunker;
    private final int writeBatchSize;


    public QdrantRagIndexProcessor(VectorStore vectorStore, RagTokenChunker chunker, RagIndexingProperties properties) {
        this.vectorStore = vectorStore;
        this.chunker = chunker;
        this.writeBatchSize = properties.writeBatchSize();
    }


    @Override
    public void process(ClaimedJob job, BooleanSupplier renewLease) throws Exception {
        if (job.operation() == RagIndexOperation.UPSERT) {
            upsert(job, renewLease);

            return;
        }

        delete(job, renewLease);
    }


    private void upsert(ClaimedJob job, BooleanSupplier renewLease) throws InterruptedException {
        RagIndexTarget target = job.target();

        if (target == null) {
            throw new IllegalStateException("UPSERT 작업에 RAG snapshot이 없습니다.");
        }

        RagSourceSnapshot snapshot = target.snapshot();
        String indexContent = snapshot.title() + "\n\n" + snapshot.content();
        List<String> chunks = chunker.split(indexContent);
        List<Document> documents = new ArrayList<>(chunks.size());

        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            documents.add(Document.builder()
                    .id(job.pointId(chunkIndex).toString())
                    .text(chunks.get(chunkIndex))
                    .metadata(createMetadata(job, snapshot, target, chunkIndex, chunks.size()))
                    .build());
        }

        for (int start = 0; start < documents.size(); start += writeBatchSize) {
            requireLease(renewLease);

            int end = Math.min(start + writeBatchSize, documents.size());
            vectorStore.add(documents.subList(start, end));

            requireLease(renewLease);
        }
    }


    private void delete(ClaimedJob job, BooleanSupplier renewLease) throws InterruptedException {
        requireLease(renewLease);

        var builder = new FilterExpressionBuilder();
        var filter = builder.and(
                        builder.and(
                                builder.eq("sourceType", job.sourceKey().sourceType().name()),
                                builder.eq("sourceId", job.sourceKey().sourceId().toString())
                        ),
                        builder.lte("sourceSequenceOrder", (double) job.sourceSequence())
                )
                .build();

        vectorStore.delete(filter);

        requireLease(renewLease);
    }


    @SuppressWarnings("DuplicatedCode")
    private Map<String, Object> createMetadata(
            ClaimedJob job, RagSourceSnapshot snapshot, RagIndexTarget target, int chunkIndex, int chunkCount
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        metadata.put("schemaVersion", 1);
        metadata.put("sourceType", snapshot.sourceType().name());
        metadata.put("sourceId", snapshot.sourceId().toString());
        metadata.put("sourceSequence", Long.toString(job.sourceSequence()));
        metadata.put("sourceSequenceOrder", (double) job.sourceSequence());
        metadata.put("visibility", snapshot.visibility().name());
        metadata.put("sourceRevision", snapshot.sourceRevision());
        metadata.put("pipelineVersion", target.pipelineVersion());
        metadata.put("generationId", job.generationId().toString());
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("chunkCount", chunkCount);
        metadata.put("title", snapshot.title());

        if (snapshot.ownerUserId() != null) {
            metadata.put("ownerUserId", snapshot.ownerUserId().toString());
        }

        if (snapshot.companyId() != null) {
            metadata.put("companyId", snapshot.companyId().toString());
        }

        if (snapshot.sourceType() == RagSourceType.JOB_POSTING) {
            metadata.put("jobPostingId", snapshot.sourceId().toString());
        }

        return Map.copyOf(metadata);
    }


    private void requireLease(BooleanSupplier renewLease) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("RAG indexing thread가 중단되었습니다.");
        }

        if (!renewLease.getAsBoolean()) {
            throw new IllegalStateException("RAG 작업 lease 소유권을 잃었습니다.");
        }
    }
}
