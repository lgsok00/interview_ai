package com.interviewai.auth.scheduler;

import com.interviewai.auth.config.RefreshTokenCleanupProperties;
import com.interviewai.auth.service.RefreshTokenCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupSchedulerTest {

    private static final int BATCH_SIZE = 1_000;
    private static final Instant EXPIRED_AT = Instant.parse("2026-08-28T00:00:00Z");

    @Mock
    private RefreshTokenCleanupService cleanupService;

    private RefreshTokenCleanupScheduler scheduler;


    @BeforeEach
    void setUp() {
        RefreshTokenCleanupProperties properties = new RefreshTokenCleanupProperties(
                true,
                Duration.ofHours(1),
                BATCH_SIZE
        );
        Clock clock = Clock.fixed(EXPIRED_AT, ZoneOffset.UTC);

        scheduler = new RefreshTokenCleanupScheduler(cleanupService, properties, clock);
    }


    @Test
    @DisplayName("Batch 크기보다 적게 삭제하면 정리를 종료한다")
    void stopsWhenDeletedCountIsLessThanBatchSize() {
        when(cleanupService.deleteExpiredBatch(EXPIRED_AT, BATCH_SIZE)).thenReturn(10);

        scheduler.cleanup();

        verify(cleanupService).deleteExpiredBatch(EXPIRED_AT, BATCH_SIZE);
    }


    @Test
    @DisplayName("Batch가 가득 차면 같은 기준 시각으로 다음 Batch를 정리한다")
    void continuesWithSameExpirationTimeWhenBatchIsFull() {
        when(cleanupService.deleteExpiredBatch(EXPIRED_AT, BATCH_SIZE))
                .thenReturn(BATCH_SIZE, BATCH_SIZE, 25);

        scheduler.cleanup();

        verify(cleanupService, times(3)).deleteExpiredBatch(EXPIRED_AT, BATCH_SIZE);
    }


    @Test
    @DisplayName("만료된 Refresh Token이 없어도 예외 없이 종료한다")
    void stopsWhenNoExpiredRefreshTokenExists() {
        when(cleanupService.deleteExpiredBatch(EXPIRED_AT, BATCH_SIZE)).thenReturn(0);

        scheduler.cleanup();

        verify(cleanupService).deleteExpiredBatch(EXPIRED_AT, BATCH_SIZE);
    }


    @Test
    @DisplayName("정리 중 예외가 발생하면 다음 Batch를 실행하지 않고 예외를 전파한다")
    void propagatesCleanupFailure() {
        IllegalStateException failure = new IllegalStateException("database failure");
        when(cleanupService.deleteExpiredBatch(EXPIRED_AT, BATCH_SIZE)).thenThrow(failure);

        assertThatThrownBy(() -> scheduler.cleanup()).isSameAs(failure);

        verify(cleanupService).deleteExpiredBatch(EXPIRED_AT, BATCH_SIZE);
    }
}
