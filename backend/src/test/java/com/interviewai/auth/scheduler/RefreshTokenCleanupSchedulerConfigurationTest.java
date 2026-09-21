package com.interviewai.auth.scheduler;

import com.interviewai.auth.service.RefreshTokenCleanupService;
import com.interviewai.global.config.SchedulingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RefreshTokenCleanupSchedulerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> {
                // Profile defaults must not depend on the developer's environment or JVM flags.
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                new ConfigDataApplicationContextInitializer().initialize(context);
            })
            .withPropertyValues("spring.config.location=classpath:/")
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

    @Test
    @DisplayName("호스트 JVM 설정을 격리하고 prod 기본값을 검증한다")
    void ignoresHostPropertiesWhenCheckingProductionDefaults() {
        contextRunner
                .withSystemProperties(
                        "REFRESH_TOKEN_CLEANUP_ENABLED=true",
                        "auth.refresh-token.cleanup.enabled=true",
                        "spring.profiles.active=local"
                )
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("prod");
                    assertThat(context.getEnvironment().getProperty(
                            "auth.refresh-token.cleanup.enabled", Boolean.class)).isFalse();
                    assertThat(context).doesNotHaveBean(RefreshTokenCleanupScheduler.class);
                });
    }

    @Test
    @DisplayName("외부 설정을 격리해도 명시적인 테스트 활성화 설정은 유지한다")
    void preservesExplicitTestOverride() {
        contextRunner
                .withSystemProperties("REFRESH_TOKEN_CLEANUP_ENABLED=false")
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "auth.refresh-token.cleanup.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RefreshTokenCleanupScheduler.class);
                });
    }
}
