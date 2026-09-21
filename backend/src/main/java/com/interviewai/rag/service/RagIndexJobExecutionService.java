package com.interviewai.rag.service;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.entity.RagIndexJobEntity;
import com.interviewai.rag.entity.RagIndexSource;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.index.RagIndexTarget;
import com.interviewai.rag.repository.RagIndexJobExecutionRepository;
import com.interviewai.rag.repository.RagIndexJobRepository;
import com.interviewai.rag.repository.RagIndexSourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.READ_COMMITTED
)
public class RagIndexJobExecutionService {

    private final RagIndexJobExecutionRepository executionRepository;
    private final RagIndexJobRepository jobRepository;
    private final RagIndexSourceRepository sourceRepository;
    private final int leaseSeconds;
    private final int retryDelaySeconds;


    public RagIndexJobExecutionService(
            RagIndexJobExecutionRepository executionRepository,
            RagIndexJobRepository jobRepository,
            RagIndexSourceRepository sourceRepository,
            @Value("${rag.worker.lease-seconds:60}") int leaseSeconds,
            @Value("${rag.worker.retry-delay-seconds:30}") int retryDelaySeconds
    ) {
        if (leaseSeconds < 1 || leaseSeconds > 86400) {
            throw new IllegalArgumentException("leaseSeconds는 1이상 86400 이하여야 합니다.");
        }

        if (retryDelaySeconds < 1 || retryDelaySeconds > 86400) {
            throw new IllegalArgumentException("retryDelaySeconds는 1이상 86400 이하여야 합니다.");
        }

        this.executionRepository = executionRepository;
        this.jobRepository = jobRepository;
        this.sourceRepository = sourceRepository;
        this.leaseSeconds = leaseSeconds;
        this.retryDelaySeconds = retryDelaySeconds;
    }


    private static void validateAttempt(long jobId, UUID attemptId) {
        if (jobId < 1) {
            throw new IllegalArgumentException("jobId는 1 이상이어야 합니다.");
        }

        Objects.requireNonNull(attemptId, "attemptId는 필수입니다.");
    }


    public Optional<ClaimedJob> claimNext() {
        Optional<Long> candidate = executionRepository.findClaimableIdLocked();

        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        long jobId = candidate.get();

        if (executionRepository.failIfAttemptsExhausted(jobId) == 1) {
            return Optional.empty();
        }

        UUID attemptId = UUID.randomUUID();

        if (executionRepository.claim(jobId, attemptId, leaseSeconds) != 1) {
            throw new IllegalStateException("잠근 RAG 작업을 선점하지 못했습니다.");
        }

        RagIndexJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("선점한 RAG 작업이 존재하지 않습니다."));

        return Optional.of(new ClaimedJob(
                job.getId(),
                attemptId,
                job.getSourceKey(),
                job.getSourceSequence(),
                job.getOperation(),
                job.getTarget(),
                job.getAttemptCount()
        ));
    }


    public boolean renew(long jobId, UUID attemptId) {
        validateAttempt(jobId, attemptId);

        return executionRepository.renew(jobId, attemptId, leaseSeconds) == 1;
    }


    public boolean succeed(long jobId, UUID attemptId) {
        validateAttempt(jobId, attemptId);

        RagIndexJobEntity job = jobRepository.findById(jobId).orElse(null);

        if (job == null) {
            return false;
        }

        RagSourceKey sourceKey = job.getSourceKey();

        RagIndexSource source = sourceRepository.findLocked(sourceKey.sourceType(), sourceKey.sourceId())
                .orElseThrow(() -> new IllegalStateException("RAG 원본 관리 행을 찾을 수 없습니다."));

        if (executionRepository.succeed(jobId, attemptId) != 1) {
            return false;
        }

        if (job.getOperation() == RagIndexOperation.UPSERT) {
            source.activate(job.getSourceSequence(), attemptId);
        }

        return true;
    }


    public boolean fail(long jobId, UUID attemptId, String failureCode) {
        validateAttempt(jobId, attemptId);
        Objects.requireNonNull(failureCode, "failureCode는 필수입니다.");

        if (!failureCode.matches("[A-Z][A-Z0-9_]{0,99}")) {
            throw new IllegalArgumentException("failureCode는 100자 이하의 영문 대문자·숫자·밑줄 코드여야 합니다.");
        }

        return executionRepository.fail(jobId, attemptId, failureCode, retryDelaySeconds) == 1;
    }


    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED,
            readOnly = true
    )
    public boolean isActiveGeneration(RagSourceKey sourceKey, UUID generationId) {
        Objects.requireNonNull(sourceKey, "sourceKey는 필수입니다.");
        Objects.requireNonNull(generationId, "generationId는 필수입니다.");

        return sourceRepository.isActiveGeneration(sourceKey.sourceType(), sourceKey.sourceId(), generationId.toString());
    }


    public record ClaimedJob(
            long jobId,
            UUID attemptId,
            RagSourceKey sourceKey,
            long sourceSequence,
            RagIndexOperation operation,
            RagIndexTarget target,
            int attemptCount
    ) {

        public UUID generationId() {
            return attemptId;
        }


        public UUID pointId(int chunkIndex) {
            if (operation != RagIndexOperation.UPSERT) {
                throw new IllegalStateException("UPSERT 작업만 point ID를 생성할 수 있습니다.");
            }

            if (chunkIndex < 0) {
                throw new IllegalArgumentException("chunkIndex는 0 이상이어야 합니다.");
            }

            String pointKey = "rag:persisted"
                    + jobId
                    + ":"
                    + generationId()
                    + ":"
                    + chunkIndex;

            return UUID.nameUUIDFromBytes(pointKey.getBytes(StandardCharsets.UTF_8));
        }
    }
}
