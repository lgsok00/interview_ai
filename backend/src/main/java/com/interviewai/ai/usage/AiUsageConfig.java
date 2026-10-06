package com.interviewai.ai.usage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiUsageProperties.class)
public class AiUsageConfig {

    public AiUsageConfig(AiUsageProperties properties, Environment environment) {
        boolean production = environment.matchesProfiles("prod");
        boolean development = environment.matchesProfiles("local", "test");

        if (!properties.enabled() && (production || !development)) {
            throw new IllegalArgumentException("AI 사용량 제한 비활성화는 local/test 환경에서만 허용합니다.");
        }
    }
}
