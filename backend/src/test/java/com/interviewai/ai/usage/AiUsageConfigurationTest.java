package com.interviewai.ai.usage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AiUsageConfigurationTest {

    static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void bindsOperatingDefaultsWithValidKey() {
        enabledRunner().run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(AiUsageProperties.class);
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.chatAdmissionEnabled()).isTrue();
            assertThat(properties.embeddingAdmissionEnabled()).isTrue();
            assertThat(properties.dailyLimit(AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(3);
            assertThat(properties.dailyLimit(AiUsageFeature.FOLLOW_UP)).isEqualTo(15);
            assertThat(properties.dailyLimit(AiUsageFeature.ANSWER_EVALUATION)).isEqualTo(30);
            assertThat(properties.dailyLimit(AiUsageFeature.COVER_LETTER_DRAFT)).isEqualTo(3);
            assertThat(properties.dailyLimit(AiUsageFeature.PERSONAL_EMBEDDING)).isEqualTo(20);
            assertThat(properties.userActiveLimit()).isEqualTo(2);
            assertThat(properties.globalActiveLimit()).isEqualTo(50);
            assertThat(properties.globalChatDailyLimit()).isEqualTo(500);
            assertThat(properties.adminEmbeddingPerMinuteLimit()).isEqualTo(10);
            assertThat(properties.followUpLeaseSeconds()).isEqualTo(90);
            assertThat(properties.toString()).doesNotContain(TEST_KEY).contains("REDACTED");
        });
    }

    @Test
    void bindsAllExternalOperatingOverridesIncludingSingularEmbeddingProperty() {
        enabledRunner().withPropertyValues(
                "ai.usage.chat-admission-enabled=false",
                "ai.usage.embedding-admission-enabled=false",
                "ai.usage.initial-questions-daily-limit=4",
                "ai.usage.follow-up-daily-limit=16",
                "ai.usage.answer-evaluation-daily-limit=31",
                "ai.usage.cover-letter-draft-daily-limit=5",
                "ai.usage.personal-embedding-daily-limit=7",
                "ai.usage.user-active-limit=3",
                "ai.usage.global-active-limit=60",
                "ai.usage.global-chat-daily-limit=600",
                "ai.usage.admin-embedding-per-minute-limit=11",
                "ai.usage.follow-up-lease-seconds=120"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(AiUsageProperties.class);
            assertThat(properties.chatAdmissionEnabled()).isFalse();
            assertThat(properties.embeddingAdmissionEnabled()).isFalse();
            assertThat(properties.dailyLimit(AiUsageFeature.INITIAL_QUESTIONS)).isEqualTo(4);
            assertThat(properties.dailyLimit(AiUsageFeature.FOLLOW_UP)).isEqualTo(16);
            assertThat(properties.dailyLimit(AiUsageFeature.ANSWER_EVALUATION)).isEqualTo(31);
            assertThat(properties.dailyLimit(AiUsageFeature.COVER_LETTER_DRAFT)).isEqualTo(5);
            assertThat(properties.dailyLimit(AiUsageFeature.PERSONAL_EMBEDDING)).isEqualTo(7);
            assertThat(properties.userActiveLimit()).isEqualTo(3);
            assertThat(properties.globalActiveLimit()).isEqualTo(60);
            assertThat(properties.globalChatDailyLimit()).isEqualTo(600);
            assertThat(properties.adminEmbeddingPerMinuteLimit()).isEqualTo(11);
            assertThat(properties.followUpLeaseSeconds()).isEqualTo(120);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "initial-questions-daily-limit,0", "initial-questions-daily-limit,-1",
            "follow-up-daily-limit,0", "answer-evaluation-daily-limit,0",
            "cover-letter-draft-daily-limit,0", "personal-embedding-daily-limit,0",
            "user-active-limit,0", "global-active-limit,0",
            "global-chat-daily-limit,0", "admin-embedding-per-minute-limit,0",
            "follow-up-lease-seconds,59", "follow-up-lease-seconds,301"
    })
    void rejectsInvalidOperatingValue(String property, int value) {
        enabledRunner().withPropertyValues("ai.usage." + property + "=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(ints = {60, 300})
    void acceptsLeaseBoundariesAndEqualSlotLimits(int leaseSeconds) {
        enabledRunner().withPropertyValues(
                "ai.usage.follow-up-lease-seconds=" + leaseSeconds,
                "ai.usage.user-active-limit=1", "ai.usage.global-active-limit=1"
        ).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsUserSlotLimitLargerThanGlobalLimit() {
        enabledRunner().withPropertyValues(
                "ai.usage.user-active-limit=3", "ai.usage.global-active-limit=2"
        ).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    void allowsExplicitDevelopmentDisableWithoutKey(String profile) {
        runner(profile).withPropertyValues("ai.usage.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "staging"})
    void rejectsDisableOutsideDevelopment(String profile) {
        runner(profile).withPropertyValues("ai.usage.enabled=false")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsDisableWithMixedProductionAndDevelopmentProfiles() {
        runner("prod", "local").withPropertyValues("ai.usage.enabled=false")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsEnabledWithoutKey() {
        runner("prod").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsInvalidKeyDuringStartup() {
        runner("prod").withPropertyValues("ai.usage.hmac-key=invalid!")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void honorsActualYamlEnvironmentOverrideForPersonalEmbedding() {
        runner("prod").withInitializer(context -> loadYaml(context.getEnvironment(), "application.yaml"))
                .withPropertyValues("AI_USAGE_HMAC_KEY=" + TEST_KEY,
                        "AI_PERSONAL_EMBEDDING_DAILY_LIMIT=7")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AiUsageProperties.class)
                            .dailyLimit(AiUsageFeature.PERSONAL_EMBEDDING)).isEqualTo(7);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    void actualDevelopmentYamlExplicitlyDisablesUsage(String profile) {
        runner(profile).withInitializer(context -> {
            loadYaml(context.getEnvironment(), "application-" + profile + ".yaml");
            loadYaml(context.getEnvironment(), "application.yaml");
        }).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AiUsageProperties.class).enabled()).isFalse();
        });
    }

    private ApplicationContextRunner enabledRunner() {
        return runner(new String[]{"prod"}).withPropertyValues("ai.usage.hmac-key=" + TEST_KEY);
    }

    private ApplicationContextRunner runner(String... profiles) {
        return new ApplicationContextRunner().withInitializer(context -> {
            var environment = context.getEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            environment.setActiveProfiles(profiles);
        }).withUserConfiguration(AiUsageConfig.class, AiUsageIdentity.class);
    }

    private void loadYaml(org.springframework.core.env.ConfigurableEnvironment environment, String resource) {
        try {
            var loader = new YamlPropertySourceLoader();
            loader.load(resource, new ClassPathResource(resource))
                    .forEach(source -> environment.getPropertySources().addLast(source));
        } catch (IOException exception) {
            throw new IllegalStateException("테스트 YAML을 읽지 못했습니다.", exception);
        }
    }
}
