package com.interviewai.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.search")
public record RagSearchProperties(
        boolean enabled
) {
}
