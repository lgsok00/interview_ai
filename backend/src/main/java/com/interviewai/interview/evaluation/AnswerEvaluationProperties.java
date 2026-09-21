package com.interviewai.interview.evaluation;

import com.interviewai.interview.generation.InterviewGenerationPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "interview.evaluation")
public record AnswerEvaluationProperties(
        @DefaultValue("false")
        boolean enabled,

        @DefaultValue("FALLBACK_ONLY")
        InterviewGenerationPolicy.Mode mode,

        @DefaultValue("")
        String model,

        @DefaultValue("1s")
        Duration fixedDelay,

        @DefaultValue("5s")
        Duration initialDelay,

        @DefaultValue("20")
        int maxJobsPerRun
) {

    public AnswerEvaluationProperties {
        if (mode == null) {
            throw new IllegalArgumentException("답변 평가 mode는 필수입니다.");
        }

        model = model == null ? "" : model.strip();

        if (model.length() > 100) {
            throw new IllegalArgumentException("답변 평가 model은 100자 이하여야 합니다.");
        }

        if (mode == InterviewGenerationPolicy.Mode.AI && model.isBlank()) {
            throw new IllegalArgumentException("AI 평가 모드에서는 model을 설정해야 합니다.");
        }

        if (fixedDelay == null || fixedDelay.isZero() || fixedDelay.isNegative()) {
            throw new IllegalArgumentException("fixedDelay는 양수여야 합니다.");
        }

        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay는 0 이상이어야 합니다.");
        }

        if (maxJobsPerRun < 1 || maxJobsPerRun > 100) {
            throw new IllegalArgumentException("maxJobsPerRun은 1 이상 100 이하여야 합니다.");
        }
    }
}
