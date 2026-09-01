package com.interviewai.auth.scheduler;

import com.interviewai.auth.service.RefreshTokenCleanupService;
import com.interviewai.global.config.SchedulingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RefreshTokenCleanupSchedulerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(
                    SchedulingConfig.class,
                    RefreshTokenCleanupScheduler.class
            )
            .withBean(
                    RefreshTokenCleanupService.class,
                    () -> mock(RefreshTokenCleanupService.class)
            );

    @Test
    @DisplayName("정리 기능을 활성화하면 Scheduler Bean을 생성한다")
    void createsSchedulerWhenCleanupIsEnabled() {
        contextRunner
                .withPropertyValues("auth.refresh-token.cleanup.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(RefreshTokenCleanupScheduler.class));
    }

    @Test
    @DisplayName("정리 기능을 비활성화하면 Scheduler Bean을 생성하지 않는다")
    void doesNotCreateSchedulerWhenCleanupIsDisabled() {
        contextRunner
                .withPropertyValues("auth.refresh-token.cleanup.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(RefreshTokenCleanupScheduler.class));
    }

    @Test
    @DisplayName("prod 프로필은 설정을 생략하면 Scheduler를 기본 비활성화한다")
    void disablesSchedulerByDefaultInProduction() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RefreshTokenCleanupScheduler.class);
                    assertThat(context.getEnvironment().getProperty(
                            "auth.refresh-token.cleanup.enabled",
                            Boolean.class
                    )).isFalse();
                });
    }

    @Test
    @DisplayName("local 프로필은 설정을 생략하면 Scheduler를 기본 활성화한다")
    void enablesSchedulerByDefaultLocally() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    assertThat(context).hasSingleBean(RefreshTokenCleanupScheduler.class);
                    assertThat(context.getEnvironment().getProperty(
                            "auth.refresh-token.cleanup.enabled",
                            Boolean.class
                    )).isTrue();
                });
    }
}
