package com.interviewai.interview.evaluation;

import com.openai.core.Timeout;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnswerEvaluationProperties.class)
public class AnswerEvaluationConfig {

    @Bean("answerEvaluationChatModel")
    @ConditionalOnProperty(prefix = "interview.evaluation", name = "mode", havingValue = "AI")
    public ChatModel answerEvaluationChatModel(
            AnswerEvaluationProperties properties,
            @Value("${spring.ai.openai.api-key:}") String apiKey
    ) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("AI 평가 모드에서는 OPENAI_API_KEY가 필요합니다.");
        }

        return OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(apiKey)
                        .model(properties.model())
                        .maxRetries(0)
                        .timeout(AnswerEvaluationPolicy.CALL_TIMEOUT)
                        .maxCompletionTokens(4000)
                        .store(false)
                        .build())
                .httpClientBuilderCustomizer(builder -> builder.timeout(
                        Timeout.builder()
                                .connect(Duration.ofSeconds(5))
                                .read(AnswerEvaluationPolicy.CALL_TIMEOUT)
                                .write(AnswerEvaluationPolicy.CALL_TIMEOUT)
                                .request(AnswerEvaluationPolicy.CALL_TIMEOUT)
                                .build()
                ))
                .build();
    }
}
