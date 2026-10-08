package com.interviewai.support;

import com.interviewai.ai.usage.AiUsageAdmissionService;
import com.interviewai.ai.usage.AiUsageIdentity;
import com.interviewai.ai.usage.AiUsageLifecycleService;
import com.interviewai.ai.usage.AiUsageProperties;
import com.interviewai.ai.usage.AiUsageRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 기존 worker 수명주기 테스트는 제한을 명시적으로 끄고, 접수 연결은 별도 통합 테스트에서 검증한다.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({AiUsageAdmissionService.class, AiUsageRepository.class, AiUsageIdentity.class, AiUsageLifecycleService.class})
public class DisabledAiUsageTestConfig {
    @Bean
    AiUsageProperties aiUsageProperties() {
        return new AiUsageProperties(false, true, true, "", 3, 15, 30, 3, 20, 2, 50, 500, 10, 90);
    }
}
