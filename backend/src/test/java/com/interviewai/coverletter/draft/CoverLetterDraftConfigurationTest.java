package com.interviewai.coverletter.draft;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CoverLetterDraftConfigurationTest {

    @Test
    void bindsSafeDefaultsWithoutExternalClient() {
        isolatedRunner().withUserConfiguration(PropertiesConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            CoverLetterDraftProperties properties = context.getBean(CoverLetterDraftProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.model()).isEmpty();
            assertThat(properties.fixedDelay()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.initialDelay()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.maxJobsPerRun()).isEqualTo(20);
        });
    }

    @Test
    void bindsExplicitSettingsAndNormalizesModel() {
        isolatedRunner().withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues(
                        "cover-letter.draft.enabled=true",
                        "cover-letter.draft.model= test-model ",
                        "cover-letter.draft.fixed-delay=2s",
                        "cover-letter.draft.initial-delay=0s",
                        "cover-letter.draft.max-jobs-per-run=100"
                ).run(context -> {
                    assertThat(context).hasNotFailed();
                    CoverLetterDraftProperties properties = context.getBean(CoverLetterDraftProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.model()).isEqualTo("test-model");
                    assertThat(properties.initialDelay()).isZero();
                    assertThat(properties.maxJobsPerRun()).isEqualTo(100);
                });
    }

    @Test
    void rejectsEnabledWithoutModelAndMissingApiKey() {
        isolatedRunner().withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("cover-letter.draft.enabled=true")
                .run(context -> assertThat(context).hasFailed());

        CoverLetterDraftConfig config = new CoverLetterDraftConfig();
        assertThatThrownBy(() -> config.coverLetterDraftChatModel(properties(true, "test-model", 1), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void schedulerIsOptIn() {
        ApplicationContextRunner runner = isolatedRunner()
                .withBean(CoverLetterDraftWorker.class, () -> mock(CoverLetterDraftWorker.class))
                .withUserConfiguration(CoverLetterDraftScheduler.class, PropertiesConfig.class);

        runner.run(context -> assertThat(context).doesNotHaveBean(CoverLetterDraftScheduler.class));
        runner.withPropertyValues("cover-letter.draft.enabled=true", "cover-letter.draft.model=test-model")
                .run(context -> assertThat(context).hasSingleBean(CoverLetterDraftScheduler.class));
    }

    @Test
    void validatesModelDelayAndPerRunBoundaries() {
        assertThatThrownBy(() -> properties(true, "x".repeat(101), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverLetterDraftProperties(false, "", Duration.ZERO, Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverLetterDraftProperties(false, "", Duration.ofSeconds(1), Duration.ofMillis(-1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(false, "", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(false, "", 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ignoresHostPropertiesButHonorsExplicitOverrides() {
        ApplicationContextRunner runner = isolatedRunner()
                .withSystemProperties("cover-letter.draft.enabled=true", "cover-letter.draft.model=host-model")
                .withUserConfiguration(PropertiesConfig.class);

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CoverLetterDraftProperties.class).enabled()).isFalse();
        });

        runner.withPropertyValues("cover-letter.draft.enabled=true", "cover-letter.draft.model=test-model")
                .run(context -> assertThat(context.getBean(CoverLetterDraftProperties.class).enabled()).isTrue());
    }

    private CoverLetterDraftProperties properties(boolean enabled, String model, int maxJobs) {
        return new CoverLetterDraftProperties(enabled, model, Duration.ofSeconds(1), Duration.ZERO, maxJobs);
    }

    private ApplicationContextRunner isolatedRunner() {
        return new ApplicationContextRunner().withInitializer(context -> {
            var sources = context.getEnvironment().getPropertySources();
            sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CoverLetterDraftProperties.class)
    static class PropertiesConfig {
    }
}
