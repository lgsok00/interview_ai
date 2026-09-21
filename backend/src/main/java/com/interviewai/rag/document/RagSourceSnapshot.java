package com.interviewai.rag.document;

import java.util.Objects;

public record RagSourceSnapshot(
        RagSourceKey sourceKey,
        Long ownerUserId,
        Long companyId,
        String title,
        String content,
        String sourceRevision
) {

    public RagSourceSnapshot {
        Objects.requireNonNull(sourceKey, "sourceKey는 필수입니다.");

        title = requireText(title, "title");
        content = requireText(content, "content");
        sourceRevision = requireText(sourceRevision, "sourceRevision");

        validateOwner(sourceKey.sourceType(), ownerUserId);
        validateCompany(sourceKey, companyId);
    }


    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "은 필수입니다.");

        String normalized = value.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "은 비어 있을 수 없습니다.");
        }

        return normalized;
    }


    private static void validateOwner(RagSourceType sourceType, Long ownerUserId) {
        if (sourceType.isPrivate()) {
            if (ownerUserId == null || ownerUserId <= 0) {
                throw new IllegalArgumentException("개인 문서에는 유효한 ownerUserId가 필요합니다.");
            }

            return;
        }

        if (ownerUserId != null) {
            throw new IllegalArgumentException("공용 문서에는 ownerUserId를 지정할 수 없습니다.");
        }
    }


    private static void validateCompany(RagSourceKey sourceKey, Long companyId) {
        RagSourceType sourceType = sourceKey.sourceType();

        if (sourceType == RagSourceType.COMPANY) {
            if (!sourceKey.sourceId().equals(companyId)) {
                throw new IllegalArgumentException("기업 문서의 companyId는 sourceId와 같아야 합니다.");
            }

            return;
        }

        if (sourceType == RagSourceType.JOB_POSTING) {
            if (companyId == null || companyId <= 0) {
                throw new IllegalArgumentException("채용공고 문서에는 유효한 companyId가 필요합니다.");
            }

            return;
        }

        if (companyId != null) {
            throw new IllegalArgumentException("개인 문서에는 companyId를 지정할 수 없습니다.");
        }
    }


    public RagSourceType sourceType() {
        return sourceKey.sourceType();
    }


    public Long sourceId() {
        return sourceKey.sourceId();
    }


    public RagVisibility visibility() {
        return sourceType().visibility();
    }
}
