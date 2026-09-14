package com.interviewai.interview.generation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterviewGenerationConfigurationTest {
    @Test
    void bindsSafeDefaultsWithoutExternalClients() {
        isolatedRunner().withUserConfiguration(PropertiesConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(InterviewGenerationProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.mode()).isEqualTo(InterviewGenerationPolicy.Mode.FALLBACK_ONLY);
            assertThat(properties.model()).isEmpty();
        });
    }

    @Test
    void rejectsAiModeWithoutModel() {
        isolatedRunner().withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("interview.generation.mode=AI")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void validatesModelLengthAndNormalizesWhitespace() {
        assertThat(new InterviewGenerationProperties(false, InterviewGenerationPolicy.Mode.AI, " model ").model())
                .isEqualTo("model");
        assertThatThrownBy(() -> new InterviewGenerationProperties(false, InterviewGenerationPolicy.Mode.AI, "x".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingApiKeyAndRagBeforeConstructingClient() {
        var config = new InterviewGenerationConfig();
        var properties = new InterviewGenerationProperties(false, InterviewGenerationPolicy.Mode.AI, "test-model");
        assertThatThrownBy(() -> config.interviewChatModel(properties, "", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("OPENAI_API_KEY");
        assertThatThrownBy(() -> config.interviewChatModel(properties, "fake-key", false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RAG");
    }

    @Test
    void schedulerIsOptInAndContainsWorkerFailure() {
        InterviewGenerationWorker worker = mock(InterviewGenerationWorker.class);
        var runner = isolatedRunner()
                .withBean(InterviewGenerationWorker.class, () -> worker)
                .withUserConfiguration(InterviewGenerationScheduler.class);
        runner.run(context -> assertThat(context).doesNotHaveBean(InterviewGenerationScheduler.class));
        runner.withPropertyValues("interview.generation.enabled=true").run(context -> {
            doThrow(new IllegalStateException("failure")).when(worker).runOnce();
            assertThatCode(() -> context.getBean(InterviewGenerationScheduler.class).process()).doesNotThrowAnyException();
            verify(worker).runOnce();
        });
    }

    @Test
    void ignoresHostPropertiesButHonorsExplicitTestOverrides() {
        var runner = isolatedRunner()
                .withSystemProperties(
                        "interview.generation.enabled=true",
                        "interview.generation.mode=AI",
                        "interview.generation.model=host-model")
                .withUserConfiguration(PropertiesConfig.class);

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(InterviewGenerationProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.mode()).isEqualTo(InterviewGenerationPolicy.Mode.FALLBACK_ONLY);
            assertThat(properties.model()).isEmpty();
        });

        runner.withPropertyValues(
                "interview.generation.enabled=true",
                "interview.generation.mode=AI",
                "interview.generation.model=test-model"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(InterviewGenerationProperties.class);
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.mode()).isEqualTo(InterviewGenerationPolicy.Mode.AI);
            assertThat(properties.model()).isEqualTo("test-model");
        });
    }

    private ApplicationContextRunner isolatedRunner() {
        return new ApplicationContextRunner().withInitializer(context -> {
            // Test @DefaultValue and opt-in behavior independently of the developer's machine.
            var sources = context.getEnvironment().getPropertySources();
            sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(InterviewGenerationProperties.class)
    static class PropertiesConfig {
    }
}
