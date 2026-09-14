package com.interviewai.interview.generation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "interview.generation", name = "enabled", havingValue = "true")
public class InterviewGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(InterviewGenerationScheduler.class);

    private final InterviewGenerationWorker worker;


    public InterviewGenerationScheduler(InterviewGenerationWorker worker) {
        this.worker = worker;
    }


    @Scheduled(
            fixedDelayString = "${interview.generation.fixed-delay:1s}",
            initialDelayString = "${interview.generation.initial-delay:5s}"
    )
    public void process() {
        try {
            worker.runOnce();

        } catch (RuntimeException exception) {
            log.error("질문 생성 worker 실행 실패: exceptionType={}", exception.getClass().getSimpleName());
        }
    }
}
