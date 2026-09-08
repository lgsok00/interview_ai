package com.interviewai.rag.service;

import com.interviewai.rag.config.RagIndexingProperties;
import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.index.RagIndexTarget;
import com.interviewai.rag.service.RagIndexJobExecutionService.ClaimedJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QdrantRagIndexProcessorTest {

    @Mock private VectorStore vectorStore;
    @Mock private RagTokenChunker chunker;
    @Mock private BooleanSupplier renewLease;

    private QdrantRagIndexProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new QdrantRagIndexProcessor(vectorStore, chunker, properties());
    }

    @Test
    void writesDeterministicDocumentsInBatchesWithPersistentMetadata() throws Exception {
        ClaimedJob job = upsertJob(RagSourceType.JOB_POSTING);
        when(chunker.split("채용공고\n\nSpring 개발자 채용"))
                .thenReturn(List.of("첫 chunk", "두 번째 chunk", "세 번째 chunk"));
        when(renewLease.getAsBoolean()).thenReturn(true);

        processor.process(job, renewLease);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(captor.capture());
        verify(renewLease, times(4)).getAsBoolean();

        List<Document> written = captor.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(2, 1);
        assertThat(written).extracting(Document::getText)
                .containsExactly("첫 chunk", "두 번째 chunk", "세 번째 chunk");

        for (int index = 0; index < written.size(); index++) {
            Document document = written.get(index);
            assertThat(document.getId()).isEqualTo(job.pointId(index).toString());
            assertThat(document.getMetadata())
                    .containsEntry("schemaVersion", 1)
                    .containsEntry("sourceType", "JOB_POSTING")
                    .containsEntry("sourceId", 20L)
                    .containsEntry("sourceSequence", 7L)
                    .containsEntry("visibility", "AUTHENTICATED_SHARED")
                    .containsEntry("generationId", job.generationId().toString())
                    .containsEntry("pipelineVersion", "rag-v1")
                    .containsEntry("companyId", 10L)
                    .containsEntry("jobPostingId", 20L)
                    .containsEntry("chunkIndex", index)
                    .containsEntry("chunkCount", 3)
                    .doesNotContainKey("ownerUserId");
        }
    }

    @Test
    void privateDocumentMetadataContainsOnlyItsOwnerScope() throws Exception {
        ClaimedJob job = upsertJob(RagSourceType.RESUME);
        when(chunker.split("이력서\n\n백엔드 개발 경력")).thenReturn(List.of("이력서 chunk"));
        when(renewLease.getAsBoolean()).thenReturn(true);

        processor.process(job, renewLease);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(captor.getValue().getFirst().getMetadata())
                .containsEntry("visibility", "PRIVATE")
                .containsEntry("ownerUserId", 7L)
                .doesNotContainKeys("companyId", "jobPostingId");
    }

    @Test
    void leaseLossBeforeFirstBatchPreventsExternalWrite() {
        ClaimedJob job = upsertJob(RagSourceType.COMPANY);
        when(chunker.split("기업\n\n기업 소개")).thenReturn(List.of("chunk"));
        when(renewLease.getAsBoolean()).thenReturn(false);

        assertThatIllegalStateException().isThrownBy(() -> processor.process(job, renewLease))
                .withMessageContaining("lease");
        verifyNoInteractions(vectorStore);
    }

    @Test
    void vectorStoreFailurePropagatesAndStopsFollowingBatches() {
        ClaimedJob job = upsertJob(RagSourceType.COMPANY);
        when(chunker.split("기업\n\n기업 소개")).thenReturn(List.of("one", "two", "three"));
        when(renewLease.getAsBoolean()).thenReturn(true);
        doThrow(new IllegalStateException("qdrant unavailable")).when(vectorStore).add(anyList());

        assertThatThrownBy(() -> processor.process(job, renewLease))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("qdrant unavailable");
        verify(vectorStore).add(anyList());
        verify(renewLease).getAsBoolean();
    }

    @Test
    void deleteUsesSourceKeyAndUpperSequenceBoundary() throws Exception {
        ClaimedJob job = deleteJob();
        when(renewLease.getAsBoolean()).thenReturn(true);

        processor.process(job, renewLease);

        ArgumentCaptor<Filter.Expression> captor = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore).delete(captor.capture());
        verify(renewLease, times(2)).getAsBoolean();

        var builder = new FilterExpressionBuilder();
        Filter.Expression expected = builder.and(
                builder.and(
                        builder.eq("sourceType", "COVER_LETTER"),
                        builder.eq("sourceId", 30L)
                ),
                builder.lte("sourceSequence", 7L)
        ).build();
        assertThat(captor.getValue()).isEqualTo(expected);
        verify(vectorStore, never()).delete(anyList());
    }

    @Test
    void leaseLossAfterDeleteDoesNotHideCompletedExternalCall() {
        ClaimedJob job = deleteJob();
        when(renewLease.getAsBoolean()).thenReturn(true, false);

        assertThatIllegalStateException().isThrownBy(() -> processor.process(job, renewLease));
        verify(vectorStore).delete(any(Filter.Expression.class));
    }

    @Test
    void rejectsUpsertWithoutSnapshotBeforeCallingDependencies() {
        ClaimedJob invalid = new ClaimedJob(
                1L,
                UUID.randomUUID(),
                new RagSourceKey(RagSourceType.COMPANY, 10L),
                7L,
                RagIndexOperation.UPSERT,
                null,
                1
        );

        assertThatIllegalStateException().isThrownBy(() -> processor.process(invalid, renewLease));
        verifyNoInteractions(chunker, vectorStore, renewLease);
    }

    private ClaimedJob upsertJob(RagSourceType type) {
        RagSourceSnapshot snapshot = snapshot(type);

        return new ClaimedJob(
                11L,
                UUID.randomUUID(),
                snapshot.sourceKey(),
                7L,
                RagIndexOperation.UPSERT,
                new RagIndexTarget(snapshot, "rag-v1"),
                1
        );
    }

    private RagSourceSnapshot snapshot(RagSourceType type) {
        long sourceId = switch (type) {
            case COMPANY -> 10L;
            case JOB_POSTING -> 20L;
            case COVER_LETTER -> 30L;
            case RESUME -> 40L;
        };
        Long ownerId = type.isPrivate() ? 7L : null;
        Long companyId = type.belongsToCompany() ? 10L : null;
        String title = switch (type) {
            case COMPANY -> "기업";
            case JOB_POSTING -> "채용공고";
            case COVER_LETTER -> "자기소개서";
            case RESUME -> "이력서";
        };
        String content = switch (type) {
            case COMPANY -> "기업 소개";
            case JOB_POSTING -> "Spring 개발자 채용";
            case COVER_LETTER -> "지원 동기";
            case RESUME -> "백엔드 개발 경력";
        };

        return new RagSourceSnapshot(
                new RagSourceKey(type, sourceId),
                ownerId,
                companyId,
                title,
                content,
                "revision-1"
        );
    }

    private ClaimedJob deleteJob() {
        return new ClaimedJob(
                12L,
                UUID.randomUUID(),
                new RagSourceKey(RagSourceType.COVER_LETTER, 30L),
                7L,
                RagIndexOperation.DELETE,
                null,
                1
        );
    }

    private RagIndexingProperties properties() {
        return new RagIndexingProperties(
                true,
                Duration.ofSeconds(1),
                Duration.ZERO,
                20,
                700,
                100,
                10000,
                2
        );
    }
}
