package com.interviewai.rag.service;

import com.interviewai.rag.config.RagIndexingProperties;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "rag.indexing",
        name = "enabled",
        havingValue = "true"
)
public class RagTokenChunker {

    private final Encoding encoding;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int maxChunks;


    public RagTokenChunker(RagIndexingProperties properties) {
        this.encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        this.chunkSize = properties.chunkSize();
        this.chunkOverlap = properties.chunkOverlap();
        this.maxChunks = properties.maxChunks();
    }


    public List<String> split(String content) {
        Objects.requireNonNull(content, "RAG 원문은 필수입니다.");

        String normalized = content.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("RAG 원문은 비어 있을 수 없습니다.");
        }

        IntArrayList tokens = encoding.encodeOrdinary(normalized);
        int step = chunkSize - chunkOverlap;
        List<String> chunks = new ArrayList<>();

        for (int start = 0; start < tokens.size(); start += step) {
            if (chunks.size() >= maxChunks) {
                throw new IllegalArgumentException("RAG 원문이 허용된 최대 chunk 수를 초과했습니다.");
            }

            int end = Math.min(start + chunkSize, tokens.size());
            IntArrayList window = new IntArrayList(end - start);

            for (int index = start; index < end; index++) {
                window.add(tokens.get(index));
            }

            String chunk = encoding.decode(window).strip();

            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end == tokens.size()) {
                break;
            }
        }

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("RAG 원문에서 색인 가능한 chunk를 생성하지 못했습니다.");
        }

        return List.copyOf(chunks);
    }
}
