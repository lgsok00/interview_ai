package com.interviewai.rag.service;

import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.search.RagSearchResult;
import com.interviewai.user.entity.User;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(
        prefix = "rag.search",
        name = "enabled",
        havingValue = "true"
)
public class RagSearchService {

    private static final int RESULT_LIMIT = 5;
    private static final int CANDIDATE_LIMIT = 50;

    private final AdminAuthorizationService authorizationService;
    private final RagIndexJobExecutionService executionService;
    private final VectorStore vectorStore;


    public RagSearchService(
            AdminAuthorizationService authorizationService,
            RagIndexJobExecutionService executionService,
            VectorStore vectorStore
    ) {
        this.authorizationService = authorizationService;
        this.executionService = executionService;
        this.vectorStore = vectorStore;
    }


    public List<RagSearchResult> search(String subject, String query) {
        User user = authorizationService.requireUser(subject);
        String normalizedQuery = requireQuery(query);

        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        var accessFilter = builder.or(
                        builder.eq("visibility", "AUTHENTICATED_SHARED"),
                        builder.and(
                                builder.eq("visibility", "PRIVATE"),
                                builder.eq("ownerUserId", user.getId())
                        )
                )
                .build();

        SearchRequest request = SearchRequest.builder()
                .query(normalizedQuery)
                .topK(CANDIDATE_LIMIT)
                .filterExpression(accessFilter)
                .build();

        List<Document> candidates = vectorStore.similaritySearch(request);

        return candidates.stream()
                .map(this::toCandidate)
                .filter(this::isActiveGeneration)
                .limit(RESULT_LIMIT)
                .map(SearchCandidate::toResult)
                .toList();
    }


    private SearchCandidate toCandidate(Document document) {
        Map<String, Object> metadata = document.getMetadata();

        RagSourceType sourceType = RagSourceType.valueOf(requireMetadata(metadata, "sourceType").toString());
        Long sourceId = toLong(requireMetadata(metadata, "sourceId"), "sourceId");
        UUID generationId = UUID.fromString(requireMetadata(metadata, "generationId").toString());
        int chunkIndex = Math.toIntExact(toLong(requireMetadata(metadata, "chunkIndex"), "chunkIndex"));
        String title = requireMetadata(metadata, "title").toString();

        return new SearchCandidate(
                sourceType,
                sourceId,
                title,
                Objects.requireNonNull(document.getText(), "검색 문서 content는 필수입니다."),
                document.getScore(),
                generationId,
                chunkIndex
        );
    }


    private boolean isActiveGeneration(SearchCandidate candidate) {
        return executionService.isActiveGeneration(
                new RagSourceKey(candidate.sourceType(), candidate.sourceId()),
                candidate.generationId()
        );
    }


    private String requireQuery(String query) {
        Objects.requireNonNull(query, "검색어는 필수입니다.");

        String normalized = query.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("검색어는 비어 있을 수 없습니다.");
        }

        if (normalized.length() > 100) {
            throw new IllegalArgumentException("검색어는 1000자 이하여야 합니다.");
        }

        return normalized;
    }


    private Object requireMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);

        if (value == null) {
            throw new IllegalStateException("RAG 검색 metadata가 누락되었습니다: " + key);
        }

        return value;
    }


    private Long toLong(Object value, String key) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.valueOf(value.toString());

        } catch (NumberFormatException exception) {
            throw new IllegalStateException("RAG 검색 metadata 숫자 형식이 올바르지 않습니다: " + key, exception);
        }
    }


    private record SearchCandidate(
            RagSourceType sourceType,
            Long sourceId,
            String title,
            String content,
            Double score,
            UUID generationId,
            int chunkIndex
    ) {

        private RagSearchResult toResult() {
            return new RagSearchResult(
                    sourceType,
                    sourceId,
                    title,
                    content,
                    score,
                    generationId,
                    chunkIndex
            );
        }
    }
}
