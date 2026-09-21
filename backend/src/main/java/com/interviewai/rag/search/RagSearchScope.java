package com.interviewai.rag.search;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.user.entity.User;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record RagSearchScope(
        User user,
        Set<RagSourceKey> allowedSourceKeys
) {

    public RagSearchScope {
        Objects.requireNonNull(user, "user는 필수입니다.");
        Objects.requireNonNull(user.getId(), "영속화된 user만 검색 범위에 사용할 수 있습니다.");
        Objects.requireNonNull(allowedSourceKeys, "allowedSourceKeys는 필수입니다.");

        if (allowedSourceKeys.isEmpty()) {
            throw new IllegalArgumentException("allowedSourceKeys는 비어 있을 수 없습니다.");
        }

        if (allowedSourceKeys.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("allowedSourceKeys는 null을 포함할 수 없습니다.");
        }

        allowedSourceKeys = Set.copyOf(new LinkedHashSet<>(allowedSourceKeys));
    }


    public boolean allows(RagSourceKey sourceKey) {
        return allowedSourceKeys.contains(sourceKey);
    }
}
