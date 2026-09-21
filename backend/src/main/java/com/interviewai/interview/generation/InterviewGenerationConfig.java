package com.interviewai.interview.generation;

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
@EnableConfigurationProperties(InterviewGenerationProperties.class)
public class InterviewGenerationConfig {

    @Bean("interviewChatModel")
    @ConditionalOnProperty(prefix = "interview.generation", name = "mode", havingValue = "AI")
    public ChatModel interviewChatModel(
            InterviewGenerationProperties properties,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${rag.search.enabled:false}") boolean ragSearchEnabled
    ) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("AI 모드에서는 OPENAI_API_KEY가 필요합니다.");
        }

        if (!ragSearchEnabled) {
            throw new IllegalArgumentException("AI 모드에서는 RAG 검색을 활성화해야 합니다.");
        }

        return OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(apiKey)
                        .model(properties.model())
                        .maxRetries(0)
                        .timeout(Duration.ofSeconds(45))
                        .maxCompletionTokens(3000)
                        .store(false)
                        .build())
                .httpClientBuilderCustomizer(builder -> builder.timeout(
                        Timeout.builder()
                                .connect(Duration.ofSeconds(5))
                                .read(Duration.ofSeconds(45))
                                .write(Duration.ofSeconds(45))
                                .request(Duration.ofSeconds(45))
                                .build()
                ))
                .build();
    }
}
