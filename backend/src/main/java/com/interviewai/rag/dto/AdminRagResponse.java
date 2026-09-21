package com.interviewai.rag.dto;

import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.index.RagIndexJobStatus;
import com.interviewai.rag.index.RagIndexOperation;

import java.time.Instant;
import java.util.List;

public final class AdminRagResponse {

    private AdminRagResponse() {

    }


    public record Source(
            long id,
            RagSourceType sourceType,
            long sourceId,
            long lastSequence,
            String activeGenerationId,
            long activeSequence,
            long tombstoneSequence
    ) {

    }


    public record Job(
            long id,
            RagSourceType sourceType,
            long sourceId,
            long sourceSequence,
            RagIndexOperation operation,
            RagIndexJobStatus status,
            int attemptCount,
            int maxAttempts,
            int manualRetryCount,
            String failureCode,
            Instant availableAt,
            Instant leaseExpiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {

    }


    public record Page<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            long totalPages
    ) {

        public Page {
            items = List.copyOf(items);
        }


        public static <T> Page<T> of(
                List<T> items,
                int page,
                int size,
                long totalElements
        ) {
            long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);

            return new Page<>(items, page, size, totalElements, totalPages);
        }
    }
}
