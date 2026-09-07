package com.interviewai.rag.service;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.entity.RagIndexJobEntity;
import com.interviewai.rag.index.RagIndexTarget;
import com.interviewai.rag.repository.RagIndexJobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

@Service
public class RagIndexJobRegistrationService {

    private final RagIndexSequenceService sequenceService;
    private final RagIndexJobRepository jobRepository;
    private final Clock clock;


    public RagIndexJobRegistrationService(
            RagIndexSequenceService sequenceService,
            RagIndexJobRepository jobRepository,
            @Qualifier("clock") Clock clock
    ) {
        this.sequenceService = sequenceService;
        this.jobRepository = jobRepository;
        this.clock = clock;
    }


    private static void validateMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts는 1 이상이어야 합니다.");
        }
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public RagIndexJobEntity registerUpsert(RagIndexTarget target, int maxAttempts) {
        Objects.requireNonNull(target, "target은 필수입니다.");
        validateMaxAttempts(maxAttempts);

        long sequence = sequenceService.allocateNext(target.snapshot().sourceKey());

        return jobRepository.save(RagIndexJobEntity.upsert(target, sequence, maxAttempts, clock.instant()));
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public RagIndexJobEntity registerDelete(RagSourceKey sourceKey, int maxAttempts) {
        Objects.requireNonNull(sourceKey, "sourceKey는 필수입니다.");
        validateMaxAttempts(maxAttempts);

        long sequence = sequenceService.allocateNext(sourceKey);

        return jobRepository.save(RagIndexJobEntity.delete(sourceKey, sequence, maxAttempts, clock.instant()));
    }
}
