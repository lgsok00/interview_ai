package com.interviewai.coverletter.draft;

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
@EnableConfigurationProperties(CoverLetterDraftProperties.class)
public class CoverLetterDraftConfig {

    @Bean("coverLetterDraftChatModel")
    @ConditionalOnProperty(prefix = "cover-letter.draft", name = "enabled", havingValue = "true")
    public ChatModel coverLetterDraftChatModel(
            CoverLetterDraftProperties properties,
            @Value("${spring.ai.openai.api-key:}") String apiKey
    ) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("자기소개서 초안 생성 활성화 시 OPENAI_API_KEY가 필요합니다.");
        }

        return OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(apiKey)
                        .model(properties.model())
                        .maxRetries(0)
                        .timeout(CoverLetterDraftPolicy.CALL_TIMEOUT)
                        .maxCompletionTokens(8000)
                        .store(false)
                        .build())
                .httpClientBuilderCustomizer(builder -> builder.timeout(
                        Timeout.builder()
                                .connect(Duration.ofSeconds(5))
                                .read(CoverLetterDraftPolicy.CALL_TIMEOUT)
                                .write(CoverLetterDraftPolicy.CALL_TIMEOUT)
                                .request(CoverLetterDraftPolicy.CALL_TIMEOUT)
                                .build()
                ))
                .build();
    }
}
