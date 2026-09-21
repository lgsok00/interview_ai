package com.interviewai.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CatalogTimeConfig {

    @Bean
    public Clock catalogClock() {
        return Clock.systemUTC();
    }
}
