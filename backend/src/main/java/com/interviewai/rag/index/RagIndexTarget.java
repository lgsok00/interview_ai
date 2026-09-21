package com.interviewai.rag.index;

import com.interviewai.rag.document.RagSourceSnapshot;

import java.util.Objects;

public record RagIndexTarget(
        RagSourceSnapshot snapshot,
        String pipelineVersion
) {

    public RagIndexTarget {
        Objects.requireNonNull(snapshot, "snapshot은 필수입니다.");
        Objects.requireNonNull(pipelineVersion, "pipelineVersion은 필수입니다.");

        pipelineVersion = pipelineVersion.strip();

        if (pipelineVersion.isEmpty() || pipelineVersion.length() > 100) {
            throw new IllegalArgumentException("pipelineVersion은 1자 이상 100자 이하여야 합니다.");
        }
    }
}
