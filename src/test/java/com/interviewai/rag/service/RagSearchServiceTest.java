package com.interviewai.rag.service;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.search.RagSearchResult;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagSearchServiceTest {

    private static final String SUBJECT = "7";
    private static final Long USER_ID = 7L;

    @Mock
    private AdminAuthorizationService authorizationService;
    @Mock
    private RagIndexJobExecutionService executionService;
    @Mock
    private RagSourceAccessService sourceAccessService;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private User user;

    private RagSearchService service;


    @BeforeEach
    void setUp() {
        service = new RagSearchService(
                authorizationService,
                executionService,
                sourceAccessService,
                vectorStore
        );
    }


    @Test
    @DisplayName("검색어를 정규화하고 사용자 범위 metadata 필터로 후보 50개를 요청한다")
    void searchesWithNormalizedQueryAndAccessFilter() {
        authenticate();
        when(user.getId()).thenReturn(USER_ID);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertThat(service.search(SUBJECT, "  Spring 백엔드  ")).isEmpty();

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());

        SearchRequest request = captor.getValue();
        assertThat(request.getQuery()).isEqualTo("Spring 백엔드");
        assertThat(request.getTopK()).isEqualTo(50);

        var builder = new FilterExpressionBuilder();
        var expectedFilter = builder.or(
                builder.eq("visibility", "AUTHENTICATED_SHARED"),
                builder.and(
                        builder.eq("visibility", "PRIVATE"),
                        builder.eq("ownerUserId", USER_ID.toString())
                )
        ).build();
        assertThat(request.getFilterExpression()).isEqualTo(expectedFilter);
        verifyNoInteractions(executionService, sourceAccessService);
    }


    @Test
    @DisplayName("활성 generation이면서 현재 원본에 접근 가능한 문서만 반환한다")
    void returnsOnlyActiveAndAccessibleDocuments() {
        authenticate();
        when(user.getId()).thenReturn(USER_ID);
        UUID inactiveGeneration = UUID.randomUUID();
        UUID inaccessibleGeneration = UUID.randomUUID();
        UUID returnedGeneration = UUID.randomUUID();
        Document inactive = document(RagSourceType.COMPANY, 10L, inactiveGeneration, 0, "비활성", 0.95);
        Document inaccessible = document(
                RagSourceType.COVER_LETTER, 20L, inaccessibleGeneration, 1, "타인 문서", 0.90
        );
        Document returned = document(RagSourceType.RESUME, 30L, returnedGeneration, 2, "내 이력서", 0.85);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(inactive, inaccessible, returned));
        when(executionService.isActiveGeneration(
                new RagSourceKey(RagSourceType.COMPANY, 10L), inactiveGeneration
        )).thenReturn(false);
        when(executionService.isActiveGeneration(
                new RagSourceKey(RagSourceType.COVER_LETTER, 20L), inaccessibleGeneration
        )).thenReturn(true);
        when(executionService.isActiveGeneration(
                new RagSourceKey(RagSourceType.RESUME, 30L), returnedGeneration
        )).thenReturn(true);
        when(sourceAccessService.canAccess(
                user, new RagSourceKey(RagSourceType.COVER_LETTER, 20L)
        )).thenReturn(false);
        when(sourceAccessService.canAccess(
                user, new RagSourceKey(RagSourceType.RESUME, 30L)
        )).thenReturn(true);

        assertThat(service.search(SUBJECT, "경력"))
                .containsExactly(new RagSearchResult(
                        RagSourceType.RESUME,
                        30L,
                        "내 이력서",
                        "내 이력서 내용",
                        0.85,
                        returnedGeneration,
                        2
                ));

        verify(sourceAccessService, never()).canAccess(
                user, new RagSourceKey(RagSourceType.COMPANY, 10L)
        );
    }


    @Test
    @DisplayName("유효한 검색 결과는 유사도 순서를 유지하며 최대 5개만 반환한다")
    void limitsResultsToFive() {
        authenticate();
        when(user.getId()).thenReturn(USER_ID);
        List<Document> documents = new ArrayList<>();

        for (long sourceId = 1; sourceId <= 6; sourceId++) {
            UUID generationId = UUID.randomUUID();
            RagSourceKey sourceKey = new RagSourceKey(RagSourceType.COMPANY, sourceId);
            documents.add(document(RagSourceType.COMPANY, sourceId, generationId, 0, "기업 " + sourceId, 1.0));
            if (sourceId <= 5) {
                when(executionService.isActiveGeneration(sourceKey, generationId)).thenReturn(true);
                when(sourceAccessService.canAccess(user, sourceKey)).thenReturn(true);
            }
        }
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(documents);

        assertThat(service.search(SUBJECT, "기업"))
                .extracting(RagSearchResult::sourceId)
                .containsExactly(1L, 2L, 3L, 4L, 5L);

        verify(executionService, never()).isActiveGeneration(
                new RagSourceKey(RagSourceType.COMPANY, 6L),
                UUID.fromString(documents.get(5).getMetadata().get("generationId").toString())
        );
    }


    @Test
    @DisplayName("검색어는 앞뒤 공백 제거 후 100자까지 허용한다")
    void acceptsMaximumQueryLength() {
        authenticate();
        when(user.getId()).thenReturn(USER_ID);
        String query = "가".repeat(100);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertThat(service.search(SUBJECT, " " + query + " ")).isEmpty();

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getQuery()).isEqualTo(query);
    }


    @Test
    @DisplayName("null, 공백 또는 100자를 초과한 검색어는 validation 오류로 거부한다")
    void rejectsInvalidQueries() {
        authenticate();

        assertThatThrownBy(() -> service.search(SUBJECT, null))
                .isInstanceOf(CatalogException.class)
                .extracting("errors")
                .isEqualTo(Map.of("query", "필수 값입니다."));
        assertThatThrownBy(() -> service.search(SUBJECT, "   "))
                .isInstanceOf(CatalogException.class);
        assertThatThrownBy(() -> service.search(SUBJECT, "가".repeat(101)))
                .isInstanceOf(CatalogException.class)
                .extracting("errors")
                .isEqualTo(Map.of("query", "100자 이하여야 합니다."));

        verifyNoInteractions(vectorStore, executionService, sourceAccessService);
    }


    @Test
    @DisplayName("사용자 인증 실패 시 검색어와 Vector Store를 처리하지 않는다")
    void rejectsInvalidSubjectBeforeSearch() {
        when(authorizationService.requireUser("invalid"))
                .thenThrow(new IllegalArgumentException("invalid subject"));

        assertThatThrownBy(() -> service.search("invalid", "검색어"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(vectorStore, executionService, sourceAccessService);
    }


    @Test
    @DisplayName("필수 metadata가 누락된 후보는 손상된 색인으로 처리한다")
    void rejectsCandidateWithMissingMetadata() {
        authenticate();
        Document invalid = Document.builder()
                .id(UUID.randomUUID().toString())
                .text("본문")
                .metadata(Map.of("sourceType", "COMPANY"))
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(invalid));

        assertThatThrownBy(() -> service.search(SUBJECT, "검색어"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sourceId");

        verifyNoInteractions(executionService, sourceAccessService);
    }


    private void authenticate() {
        when(authorizationService.requireUser(SUBJECT)).thenReturn(user);
    }


    private Document document(
            RagSourceType sourceType,
            long sourceId,
            UUID generationId,
            int chunkIndex,
            String title,
            double score
    ) {
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .text(title + " 내용")
                .metadata(Map.of(
                        "sourceType", sourceType.name(),
                        "sourceId", Long.toString(sourceId),
                        "generationId", generationId.toString(),
                        "chunkIndex", chunkIndex,
                        "title", title
                ))
                .score(score)
                .build();
    }
}
