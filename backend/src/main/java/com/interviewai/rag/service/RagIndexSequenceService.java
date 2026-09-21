package com.interviewai.rag.service;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.entity.RagIndexSource;
import com.interviewai.rag.repository.RagIndexSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class RagIndexSequenceService {

    private final RagIndexSourceRepository ragIndexSourceRepository;


    public RagIndexSequenceService(RagIndexSourceRepository ragIndexSourceRepository) {
        this.ragIndexSourceRepository = ragIndexSourceRepository;
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public long allocateNext(RagSourceKey sourceKey) {
        return findSourceLocked(sourceKey).allocateNextSequence();
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public long allocateNextForDelete(RagSourceKey sourceKey) {
        RagIndexSource source = findSourceLocked(sourceKey);
        long sequence = source.allocateNextSequence();

        source.recordDelete(sequence);

        return sequence;
    }


    private RagIndexSource findSourceLocked(RagSourceKey sourceKey) {
        Objects.requireNonNull(sourceKey, "sourceKey는 필수입니다.");

        ragIndexSourceRepository.ensureExists(sourceKey.sourceType().name(), sourceKey.sourceId());

        return ragIndexSourceRepository.findLocked(sourceKey.sourceType(), sourceKey.sourceId())
                .orElseThrow(() -> new IllegalStateException("RAG 원본 관리 행을 찾을 수 없습니다."));
    }
}
