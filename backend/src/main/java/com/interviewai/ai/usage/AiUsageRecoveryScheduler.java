package com.interviewai.ai.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "ai.usage",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AiUsageRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiUsageRecoveryScheduler.class);

    private final AiUsageLifecycleService lifecycle;


    public AiUsageRecoveryScheduler(AiUsageLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }


    @Scheduled(
            fixedDelayString = "${ai.usage.recovery-fixed-delay:30s}",
            initialDelayString = "${ai.usage.recovery-initial-delay:10s}"
    )
    public void recover() {
        try {
            lifecycle.recoverReservations();

        } catch (RuntimeException exception) {
            log.error("AI 실행 예약 복구 실패: exceptionType={}", exception.getClass().getSimpleName());
        }
    }
}
