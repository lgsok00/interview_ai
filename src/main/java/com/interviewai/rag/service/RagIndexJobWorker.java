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
         * 긴 작업은 lease가 만료되기 전에 renewLease를 호출한다.
         * false가 반환되면 소유권을 잃었으므로 처리를 중단한다.
         * <p>
         * 재실행될 수 있으므로 외부 쓰기는 멱등성을 보장해야 한다.
         * 외부 호출에는 lease 정책에 맞는 timeout을 설정해야 한다.
         */
        void process(ClaimedJob job, BooleanSupplier renewLease) throws Exception;
    }
}
