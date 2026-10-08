package com.interviewai.ai.usage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class AiUsageRecoverySchedulerTest {
    private final AiUsageLifecycleService lifecycle = mock(AiUsageLifecycleService.class);

    @Test
    void invokesRecoveryOncePerTick() {
        new AiUsageRecoveryScheduler(lifecycle).recover();
        verify(lifecycle).recoverReservations();
        verifyNoMoreInteractions(lifecycle);
    }

    @Test
    void failureDoesNotPreventNextTickFromTryingAgain() {
        when(lifecycle.recoverReservations()).thenThrow(new IllegalStateException("DB failed")).thenReturn(0);
        var scheduler = new AiUsageRecoveryScheduler(lifecycle);
        assertThatCode(scheduler::recover).doesNotThrowAnyException();
        assertThatCode(scheduler::recover).doesNotThrowAnyException();
        verify(lifecycle, times(2)).recoverReservations();
    }

    @Test
    void enabledByDefaultAndDisabledExplicitly() {
        var runner = new ApplicationContextRunner()
                .withBean(AiUsageLifecycleService.class, () -> lifecycle)
                .withUserConfiguration(AiUsageRecoveryScheduler.class);
        runner.run(context -> assertThat(context).hasSingleBean(AiUsageRecoveryScheduler.class));
        runner.withPropertyValues("ai.usage.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AiUsageRecoveryScheduler.class));
    }
}
