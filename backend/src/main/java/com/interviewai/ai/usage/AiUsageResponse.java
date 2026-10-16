package com.interviewai.ai.usage;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AiUsageResponse(
        boolean enabled,
        boolean chatAdmissionEnabled,
        String timeZone,
        LocalDate usageDate,
        OffsetDateTime resetAt,
        Long active,
        Integer activeLimit,
        List<FeatureUsage> features
) {

    public record FeatureUsage(
            AiUsageFeature feature,
            int limit,
            int used,
            int remaining
    ) {

    }
}
