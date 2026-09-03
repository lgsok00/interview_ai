package com.interviewai.resume.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ResumeFileProperties.class)
public class ResumeFileStorageConfig {

}
