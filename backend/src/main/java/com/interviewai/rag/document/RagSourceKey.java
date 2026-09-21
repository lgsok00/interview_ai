package com.interviewai.rag.document;

import java.util.Objects;

public record RagSourceKey(
        RagSourceType sourceType,
        Long sourceId
) {

    public RagSourceKey {
        Objects.requireNonNull(sourceType, "sourceType은 필수입니다.");
        Objects.requireNonNull(sourceId, "sourceId는 필수입니다.");

        if (sourceId <= 0) {
            throw new IllegalArgumentException("sourceId는 양수여야 합니다.");
        }
    }
}
