package com.interviewai.rag.document;

import java.util.Objects;
import java.util.Optional;

public record RagSourceResolution(
        RagSourceKey sourceKey,
        RagSourceStatus status,
        RagSourceSnapshot snapshot
) {

    public RagSourceResolution {
        Objects.requireNonNull(sourceKey, "sourceKey는 필수입니다.");
        Objects.requireNonNull(status, "status는 필수입니다.");

        if (status == RagSourceStatus.READY && snapshot == null) {
            throw new IllegalArgumentException("READY 상태에서는 snapshot이 필요합니다.");
        }

        if (status != RagSourceStatus.READY && snapshot != null) {
            throw new IllegalArgumentException("READY가 아닌 상태에는 snapshot을 지정할 수 없습니다.");
        }

        if (snapshot != null && !sourceKey.equals(snapshot.sourceKey())) {
            throw new IllegalArgumentException("sourceKey와 snapshot의 sourceKey가 일치해야 합니다.");
        }
    }


    public static RagSourceResolution ready(RagSourceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot은 필수입니다.");

        return new RagSourceResolution(snapshot.sourceKey(), RagSourceStatus.READY, snapshot);
    }


    public static RagSourceResolution unavailable(RagSourceKey sourceKey, RagSourceStatus status) {
        if (status == RagSourceStatus.READY) {
            throw new IllegalArgumentException("READY 상태는 unavailable 결과로 생성할 수 없습니다.");
        }

        return new RagSourceResolution(sourceKey, status, null);
    }


    public boolean isReady() {
        return status == RagSourceStatus.READY;
    }


    public Optional<RagSourceSnapshot> optionalSnapshot() {
        return Optional.ofNullable(snapshot);
    }
}
