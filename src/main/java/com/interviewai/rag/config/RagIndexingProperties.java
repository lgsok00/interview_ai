package com.interviewai.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "rag.indexing")
public record RagIndexingProperties(
        boolean enabled,
        Duration fixedDelay,
        Duration initialDelay,
        int maxJobsPerRun,
        int chunkSize,
        int chunkOverlap,
        int maxChunks,
        int writeBatchSize
) {

    public RagIndexingProperties {
        if (fixedDelay == null || fixedDelay.isZero() || fixedDelay.isNegative()) {
            throw new IllegalArgumentException("RAG indexing 실행 주기는 0보다 커야 합니다.");
        }

        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("RAG indexing 초기 지연은 0 이상이어야 합니다.");
        }

        if (maxJobsPerRun < 1) {
            throw new IllegalArgumentException("RAG indexing 1회 최대 작업 수는 1 이상이어야 합니다.");
        }

        if (chunkSize < 1) {
            throw new IllegalArgumentException("RAG chunk 크기는 1 이상이어야 합니다.");
        }

        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("RAG chunk overlap은 0 이상 chunk 크기 미만이어야 합니다.");
        }

        if (maxChunks < 1) {
            throw new IllegalArgumentException("RAG 최대 chunk 수는 1 이상이어야 합니다.");
        }

        if (writeBatchSize < 1) {
            throw new IllegalArgumentException("Qdrant 쓰기 batch 크기는 1 이상이어야 합니다.");
        }
    }
}
