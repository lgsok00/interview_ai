package com.interviewai.interview.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "interview.evaluation", name = "enabled", havingValue = "true")
public class AnswerEvaluationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnswerEvaluationScheduler.class);

    private final AnswerEvaluationWorker worker;
    private final AnswerEvaluationProperties properties;


    public AnswerEvaluationScheduler(AnswerEvaluationWorker worker, AnswerEvaluationProperties properties) {
        this.worker = worker;
        this.properties = properties;
    }


    @Scheduled(
            fixedDelayString = "${interview.evaluation.fixed-delay:1s}",
            initialDelayString = "${interview.evaluation.initial-delay:5s}"
    )
    public void process() {
        for (int count = 0; count < properties.maxJobsPerRun(); count++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            try {
                if (!worker.runOnce()) {
                    return;
                }

            } catch (RuntimeException exception) {
                log.error("답변 평가 worker 실행 실패: exceptionType={}", exception.getClass().getSimpleName());
                return;
            }
        }
    }
}
