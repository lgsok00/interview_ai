package com.interviewai.rag.service;

import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.global.validation.CatalogInput;
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
    private final RagSourceAccessService sourceAccessService;
    private final VectorStore vectorStore;


    public RagSearchService(
            AdminAuthorizationService authorizationService,
            RagIndexJobExecutionService executionService,
            RagSourceAccessService sourceAccessService,
            VectorStore vectorStore
    ) {
        this.authorizationService = authorizationService;
        this.executionService = executionService;
        this.sourceAccessService = sourceAccessService;
        this.vectorStore = vectorStore;
    }


    public List<RagSearchResult> search(String subject, String query) {
        User user = authorizationService.requireUser(subject);
        String normalizedQuery = Objects.requireNonNull(CatalogInput.text(query, "query", 100, true));

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

        if (candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .map(this::toCandidate)
                .filter(this::isActiveGeneration)
                .filter(candidate -> sourceAccessService.canAccess(user, candidate.sourceKey()))
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
        return executionService.isActiveGeneration(candidate.sourceKey(), candidate.generationId());
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


        private RagSourceKey sourceKey() {
            return new RagSourceKey(sourceType, sourceId);
        }
    }
}
