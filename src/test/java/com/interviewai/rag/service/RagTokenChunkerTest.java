package com.interviewai.rag.service;

import com.interviewai.rag.config.RagIndexingProperties;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class RagTokenChunkerTest {

    private static final int TEST_CHUNK_SIZE = 10;
    private static final int TEST_CHUNK_OVERLAP = 3;

    @Test
    void keepsConfiguredTokenOverlapBetweenChunks() {
        RagTokenChunker chunker =
                new RagTokenChunker(properties(TEST_CHUNK_SIZE, TEST_CHUNK_OVERLAP, 10));
        String content = "Spring AI Qdrant embedding processor scheduler generation metadata "
                + "cover letter resume company job posting interview context";

        var chunks = chunker.split(content);
        var encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        var tokens = encoding.encodeOrdinary(content);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(encoding.encodeOrdinary(chunk).size()).isLessThanOrEqualTo(TEST_CHUNK_SIZE));

        String expectedSecond = encoding.decode(secondChunkWindow(tokens)).strip();
        assertThat(chunks.get(1)).isEqualTo(expectedSecond);
    }

    @Test
    void returnsOneImmutableChunkForShortNormalizedContent() {
        RagTokenChunker chunker = new RagTokenChunker(properties(20, 5, 10));

        var chunks = chunker.split("  짧은 자기소개서 본문  ");

        assertThat(chunks).containsExactly("짧은 자기소개서 본문");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> chunks.add("변경"));
    }

    @Test
    void rejectsMissingAndBlankContent() {
        RagTokenChunker chunker = new RagTokenChunker(properties(20, 5, 10));

        assertThatNullPointerException().isThrownBy(() -> chunker.split(null));
        assertThatIllegalArgumentException().isThrownBy(() -> chunker.split(" \n\t "));
    }

    @Test
    void rejectsContentExceedingMaximumChunkCount() {
        RagTokenChunker chunker = new RagTokenChunker(properties(5, 1, 1));

        assertThatIllegalArgumentException().isThrownBy(() -> chunker.split(
                "company posting resume cover letter interview question evaluation growth analysis"));
    }

    private IntArrayList secondChunkWindow(IntArrayList source) {
        int start = TEST_CHUNK_SIZE - TEST_CHUNK_OVERLAP;
        int end = Math.min(start + TEST_CHUNK_SIZE, source.size());
        IntArrayList result = new IntArrayList(end - start);

        for (int index = start; index < end; index++) {
            result.add(source.get(index));
        }

        return result;
    }

    private RagIndexingProperties properties(int chunkSize, int overlap, int maxChunks) {
        return new RagIndexingProperties(
                true,
                Duration.ofSeconds(1),
                Duration.ZERO,
                20,
                chunkSize,
                overlap,
                maxChunks,
                2
        );
    }
}
