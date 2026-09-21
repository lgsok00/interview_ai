package com.interviewai.coverletter.draft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "cover-letter.draft", name = "enabled", havingValue = "true")
public class CoverLetterDraftScheduler {

    private static final Logger log = LoggerFactory.getLogger(CoverLetterDraftScheduler.class);

    private final CoverLetterDraftWorker worker;
    private final CoverLetterDraftProperties properties;


    public CoverLetterDraftScheduler(CoverLetterDraftWorker worker, CoverLetterDraftProperties properties) {
        this.worker = worker;
        this.properties = properties;
    }


    @Scheduled(
            fixedDelayString = "${cover-letter.draft.fixed-delay:1s}",
            initialDelayString = "${cover-letter.draft.initial-delay:5s}"
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
                log.error("자기소개서 초안 worker 실행 실패: exceptionType={}", exception.getClass().getSimpleName());

                return;
            }
        }
    }
}
