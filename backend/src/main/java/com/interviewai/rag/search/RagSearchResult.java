package com.interviewai.rag.search;

import com.interviewai.rag.document.RagSourceType;

import java.util.Objects;
import java.util.UUID;

public record RagSearchResult(
        RagSourceType sourceType,
        Long sourceId,
        String title,
        String content,
        Double score,
        UUID generationId,
        int chunkIndex
) {

    public RagSearchResult {
        Objects.requireNonNull(sourceType, "sourceType은 필수입니다.");
        Objects.requireNonNull(sourceId, "sourceId는 필수입니다.");
        Objects.requireNonNull(title, "title은 필수입니다.");
        Objects.requireNonNull(content, "content는 필수입니다.");
        Objects.requireNonNull(generationId, "generationId는 필수입니다.");

        if (sourceId <= 0) {
            throw new IllegalArgumentException("sourceId는 1 이상이어야 합니다.");
        }

        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex는 0 이상이어야 합니다.");
        }
    }
}
