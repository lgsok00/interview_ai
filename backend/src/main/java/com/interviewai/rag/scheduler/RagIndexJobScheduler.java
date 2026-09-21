package com.interviewai.rag.scheduler;

import com.interviewai.rag.config.RagIndexingProperties;
import com.interviewai.rag.service.QdrantRagIndexProcessor;
import com.interviewai.rag.service.RagIndexJobWorker;
import com.interviewai.rag.service.RagIndexJobWorker.RunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "rag.indexing",
        name = "enabled",
        havingValue = "true"
)
public class RagIndexJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(RagIndexJobScheduler.class);

    private final RagIndexJobWorker worker;
    private final QdrantRagIndexProcessor processor;
    private final int maxJobsPerRun;


    public RagIndexJobScheduler(
            RagIndexJobWorker worker,
            QdrantRagIndexProcessor processor,
            RagIndexingProperties properties
    ) {
        this.worker = worker;
        this.processor = processor;
        this.maxJobsPerRun = properties.maxJobsPerRun();
    }


    @Scheduled(
            fixedDelayString = "${rag.indexing.fixed-delay}",
            initialDelayString = "${rag.indexing.initial-delay}"
    )
    public void processPendingJobs() {
        int processedCount = 0;

        while (processedCount < maxJobsPerRun) {
            RunResult result;

            try {
                result = worker.runOnce(processor);

            } catch (RuntimeException exception) {
                log.error("RAG indexing scheduler 실행 실패", exception);

                return;
            }

            if (result == RunResult.NO_JOB) {
                return;
            }

            processedCount++;

            if (result == RunResult.FAILURE_RECORDED) {
                log.warn("RAG indexing 실패를 기록했습니다.");

            } else if (result == RunResult.LEASE_LOST) {
                log.warn("RAG indexing 작업의 lease 소유권을 잃었습니다.");
            }
        }

        log.debug("RAG indexing scheduler 처리 한도 도달: processedCount = {}", processedCount);
    }
}
