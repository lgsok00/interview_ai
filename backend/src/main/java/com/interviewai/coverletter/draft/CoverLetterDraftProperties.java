package com.interviewai.coverletter.draft;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "cover-letter.draft")
public record CoverLetterDraftProperties(
        @DefaultValue("false")
        boolean enabled,

        @DefaultValue("")
        String model,

        @DefaultValue("1s")
        Duration fixedDelay,

        @DefaultValue("5s")
        Duration initialDelay,

        @DefaultValue("20")
        int maxJobsPerRun
) {

    public CoverLetterDraftProperties {
        model = model == null ? "" : model.strip();

        if (model.length() > 100) {
            throw new IllegalArgumentException("자기소개서 초안 model은 100자 이하여야 합니다.");
        }

        if (enabled && model.isBlank()) {
            throw new IllegalArgumentException("자기소개서 초안 생성 활성화 시 model이 필요합니다.");
        }

        if (fixedDelay == null || fixedDelay.isZero() || fixedDelay.isNegative()) {
            throw new IllegalArgumentException("자기소개서 초안 fixedDelay는 양수여야 합니다.");
        }

        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("자기소개서 초안 initialDelay는 0 이상이어야 합니다.");
        }

        if (maxJobsPerRun < 1 || maxJobsPerRun > 100) {
            throw new IllegalArgumentException("자기소개서 초안 maxJobsPerRun은 1 이상 100 이하여야 합니다.");
        }
    }
}
