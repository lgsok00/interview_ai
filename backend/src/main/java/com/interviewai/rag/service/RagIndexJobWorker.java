package com.interviewai.rag.service;

import com.interviewai.rag.service.RagIndexJobExecutionService.ClaimedJob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

@Service
public class RagIndexJobWorker {

    private final RagIndexJobExecutionService executionService;


    public RagIndexJobWorker(RagIndexJobExecutionService executionService) {
        this.executionService = executionService;
    }


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunResult runOnce(Processor processor) {
        Objects.requireNonNull(processor, "processor는 필수입니다.");

        Optional<ClaimedJob> candidate = executionService.claimNext();

        if (candidate.isEmpty()) {
            return RunResult.NO_JOB;
        }

        ClaimedJob job = candidate.get();

        try {
            processor.process(job, () -> executionService.renew(job.jobId(), job.attemptId()));

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            return recordFailure(job, "PROCESSING_INTERRUPTED");

        } catch (Exception exception) {
            return recordFailure(job, "PROCESSING_FAILED");
        }

        boolean accepted = executionService.succeed(job.jobId(), job.attemptId());

        return accepted ? RunResult.SUCCEEDED : RunResult.LEASE_LOST;
    }


    private RunResult recordFailure(ClaimedJob job, String failureCode) {
        boolean accepted = executionService.fail(job.jobId(), job.attemptId(), failureCode);

        return accepted ? RunResult.FAILURE_RECORDED : RunResult.LEASE_LOST;
    }


    public enum RunResult {
        NO_JOB,
        SUCCEEDED,
        FAILURE_RECORDED,
        LEASE_LOST
    }


    @FunctionalInterface
    public interface Processor {

        /**
         * 등록 당시 스냅샷 또는 DELETE 키를 처리한다.
         * <p>
         * UPSERT는 job.generationId()와 job.pointId(chunkIndex)를 사용해
         * 실행 시도별로 분리된 외부 데이터를 저장한다.
         * metadata에는 sourceType, sourceId, sourceSequence, generationId를 포함한다.
         * 모든 chunk의 저장 완료가 확인된 후 반환해야 한다.
         * 활성 generation 교체는 worker의 작업 성공 트랜잭션에서 수행한다.
         * <p>
         * DELETE의 검색 무효화는 등록 트랜잭션의 tombstone으로 처리된다.
         * 외부 정리는 해당 원본의 sourceSequence가 DELETE 순번 이하인
         * 데이터에만 적용하며, 원본 키만으로 전체 삭제하지 않는다.
         * <p>
         * 긴 작업은 lease가 만료되기 전에 renewLease를 호출한다.
         * false가 반환되면 소유권을 잃었으므로 처리를 중단한다.
         * <p>
         * 외부 호출에는 timeout을 설정하고 동일 실행 내 쓰기의 멱등성을 보장한다.
         * 늦게 도착한 비활성 generation 데이터는 검색 후보 검증에서 제외하고
         * 후속 정리 대상으로 취급한다.
         */
        void process(ClaimedJob job, BooleanSupplier renewLease) throws Exception;
    }
}
