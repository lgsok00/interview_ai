package com.interviewai.interview.generation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "interview.generation")
public record InterviewGenerationProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("FALLBACK_ONLY") InterviewGenerationPolicy.Mode mode,
        @DefaultValue("") String model
) {

    public InterviewGenerationProperties {
        if (mode == null) {
            throw new IllegalArgumentException("질문 생성 mode는 필수입니다.");
        }

        model = model == null ? "" : model.strip();

        if (model.length() > 100) {
            throw new IllegalArgumentException("model은 100자 이하여야 합니다.");
        }

        if (mode == InterviewGenerationPolicy.Mode.AI && model.isBlank()) {
            throw new IllegalArgumentException("AI 모드에서는 model을 설정해야 합니다.");
        }
    }
}
