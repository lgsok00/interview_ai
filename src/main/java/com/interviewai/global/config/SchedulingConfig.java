package com.interviewai.global.config;

import com.interviewai.auth.config.RefreshTokenCleanupProperties;
import com.interviewai.rag.config.RagIndexingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        RefreshTokenCleanupProperties.class,
        RagIndexingProperties.class
})
public class SchedulingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
