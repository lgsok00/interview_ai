package com.interviewai.auth.scheduler;

import com.interviewai.auth.config.RefreshTokenCleanupProperties;
import com.interviewai.auth.service.RefreshTokenCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@ConditionalOnProperty(
        prefix = "auth.refresh-token.cleanup",
        name = "enabled",
        havingValue = "true"
)
public class RefreshTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

    private final RefreshTokenCleanupService cleanupService;
    private final RefreshTokenCleanupProperties properties;
    private final Clock clock;


    public RefreshTokenCleanupScheduler(
            RefreshTokenCleanupService cleanupService, RefreshTokenCleanupProperties properties, Clock clock) {
        this.cleanupService = cleanupService;
        this.properties = properties;
        this.clock = clock;
    }


    @Scheduled(
            fixedDelayString = "${auth.refresh-token.cleanup.fixed-delay}",
            initialDelayString = "${auth.refresh-token.cleanup.initial-delay}"
    )
    public void cleanup() {
        Instant expiredAt = clock.instant();
        int totalDeleted = 0;
        int deleted;

        do {
            deleted = cleanupService.deleteExpiredBatch(expiredAt, properties.batchSize());
            totalDeleted += deleted;

        } while (deleted == properties.batchSize());

        if (totalDeleted > 0) {
            log.info("만료 Refresh Token 정리 완료: deletedCount = {}", totalDeleted);
        }
    }
}
