package com.interviewai.interview.evaluation;

import com.interviewai.interview.generation.InterviewGenerationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AnswerEvaluationConfigurationTest {

    @Test
    void bindsSafeDefaultsWithoutExternalClient() {
        isolatedRunner().withUserConfiguration(PropertiesConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(AnswerEvaluationProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.mode()).isEqualTo(InterviewGenerationPolicy.Mode.FALLBACK_ONLY);
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
                        "interview.evaluation.enabled=true",
                        "interview.evaluation.mode=AI",
                        "interview.evaluation.model= test-model ",
                        "interview.evaluation.fixed-delay=2s",
                        "interview.evaluation.initial-delay=0s",
                        "interview.evaluation.max-jobs-per-run=100"
                ).run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(AnswerEvaluationProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.mode()).isEqualTo(InterviewGenerationPolicy.Mode.AI);
                    assertThat(properties.model()).isEqualTo("test-model");
                    assertThat(properties.fixedDelay()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.initialDelay()).isZero();
                    assertThat(properties.maxJobsPerRun()).isEqualTo(100);
                });
    }

    @Test
    void rejectsAiModeWithoutModelAndMissingApiKey() {
        isolatedRunner().withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("interview.evaluation.mode=AI")
                .run(context -> assertThat(context).hasFailed());

        var config = new AnswerEvaluationConfig();
        var properties = properties(InterviewGenerationPolicy.Mode.AI, "test-model", Duration.ofSeconds(1), Duration.ZERO, 1);
        assertThatThrownBy(() -> config.answerEvaluationChatModel(properties, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void schedulerIsOptIn() {
        var runner = isolatedRunner()
                .withBean(AnswerEvaluationWorker.class, () -> mock(AnswerEvaluationWorker.class))
                .withUserConfiguration(AnswerEvaluationScheduler.class, PropertiesConfig.class);

        runner.run(context -> assertThat(context).doesNotHaveBean(AnswerEvaluationScheduler.class));
        runner.withPropertyValues("interview.evaluation.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(AnswerEvaluationScheduler.class));
    }

    @Test
    void validatesModelDelayAndPerRunBoundaries() {
        assertThatThrownBy(() -> properties(InterviewGenerationPolicy.Mode.AI, "x".repeat(101), Duration.ofSeconds(1), Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(InterviewGenerationPolicy.Mode.FALLBACK_ONLY, "", Duration.ZERO, Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(InterviewGenerationPolicy.Mode.FALLBACK_ONLY, "", Duration.ofSeconds(1), Duration.ofMillis(-1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(InterviewGenerationPolicy.Mode.FALLBACK_ONLY, "", Duration.ofSeconds(1), Duration.ZERO, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(InterviewGenerationPolicy.Mode.FALLBACK_ONLY, "", Duration.ofSeconds(1), Duration.ZERO, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ignoresHostPropertiesButHonorsExplicitOverrides() {
        var runner = isolatedRunner()
                .withSystemProperties(
                        "interview.evaluation.enabled=true",
                        "interview.evaluation.mode=AI",
                        "interview.evaluation.model=host-model")
                .withUserConfiguration(PropertiesConfig.class);

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(AnswerEvaluationProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.mode()).isEqualTo(InterviewGenerationPolicy.Mode.FALLBACK_ONLY);
            assertThat(properties.model()).isEmpty();
        });

        runner.withPropertyValues(
                "interview.evaluation.enabled=true",
                "interview.evaluation.mode=AI",
                "interview.evaluation.model=test-model"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AnswerEvaluationProperties.class).enabled()).isTrue();
        });
    }

    private AnswerEvaluationProperties properties(
            InterviewGenerationPolicy.Mode mode,
            String model,
            Duration fixedDelay,
            Duration initialDelay,
            int maxJobsPerRun
    ) {
        return new AnswerEvaluationProperties(false, mode, model, fixedDelay, initialDelay, maxJobsPerRun);
    }

    private ApplicationContextRunner isolatedRunner() {
        return new ApplicationContextRunner().withInitializer(context -> {
            var sources = context.getEnvironment().getPropertySources();
            sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AnswerEvaluationProperties.class)
    static class PropertiesConfig {
    }
}
