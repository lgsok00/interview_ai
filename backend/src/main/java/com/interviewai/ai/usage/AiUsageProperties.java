package com.interviewai.ai.usage;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ai.usage")
public record AiUsageProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean chatAdmissionEnabled,
        @DefaultValue("true") boolean embeddingAdmissionEnabled,
        @DefaultValue("") String hmacKey,
        @DefaultValue("3") int initialQuestionsDailyLimit,
        @DefaultValue("15") int followUpDailyLimit,
        @DefaultValue("30") int answerEvaluationDailyLimit,
        @DefaultValue("3") int coverLetterDraftDailyLimit,
        @DefaultValue("20") int personalEmbeddingDailyLimit,
        @DefaultValue("2") int userActiveLimit,
        @DefaultValue("50") int globalActiveLimit,
        @DefaultValue("500") int globalChatDailyLimit,
        @DefaultValue("10") int adminEmbeddingPerMinuteLimit,
        @DefaultValue("90") int followUpLeaseSeconds
) {

    public AiUsageProperties {
        hmacKey = hmacKey == null ? "" : hmacKey.strip();

        requirePositive("initialQuestionsDailyLimit", initialQuestionsDailyLimit);
        requirePositive("followUpDailyLimit", followUpDailyLimit);
        requirePositive("answerEvaluationDailyLimit", answerEvaluationDailyLimit);
        requirePositive("coverLetterDraftDailyLimit", coverLetterDraftDailyLimit);
        requirePositive("personalEmbeddingDailyLimit", personalEmbeddingDailyLimit);
        requirePositive("userActiveLimit", userActiveLimit);
        requirePositive("globalActiveLimit", globalActiveLimit);
        requirePositive("globalChatDailyLimit", globalChatDailyLimit);
        requirePositive("adminEmbeddingPerMinuteLimit", adminEmbeddingPerMinuteLimit);

        if (userActiveLimit > globalActiveLimit) {
            throw new IllegalArgumentException("사용자 실행 한도는 전체 실행 한도 이하여야 합니다.");
        }

        if (followUpLeaseSeconds < 60 || followUpLeaseSeconds > 300) {
            throw new IllegalArgumentException("꼬리 질문 lease는 60~300초여야 합니다.");
        }

        if (enabled && hmacKey.isBlank()) {
            throw new IllegalArgumentException("AI 사용량 제한을 활성화하려면 AI_USAGE_HMAC_KEY가 필요합니다.");
        }
    }

    private static void requirePositive(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(name + "은 1 이상이어야 합니다.");
        }
    }

    public int dailyLimit(AiUsageFeature feature) {
        return switch (feature) {
            case INITIAL_QUESTIONS -> initialQuestionsDailyLimit;
            case FOLLOW_UP -> followUpDailyLimit;
            case ANSWER_EVALUATION -> answerEvaluationDailyLimit;
            case COVER_LETTER_DRAFT -> coverLetterDraftDailyLimit;
            case PERSONAL_EMBEDDING -> personalEmbeddingDailyLimit;
        };
    }

    @Override
    public @NonNull String toString() {
        return "AiUsageProperties[enabled=" + enabled + ", hmackey=REDACTED]";
    }
}
