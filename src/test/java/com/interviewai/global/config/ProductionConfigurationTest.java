package com.interviewai.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "DB_URL=jdbc:mysql://production-db:3306/interview_ai",
                    "DB_USERNAME=interview_ai",
                    "DB_PASSWORD=production-password",
                    "JWT_SECRET=production-jwt-secret-that-is-at-least-32-bytes-long",
                    "GOOGLE_CLIENT_ID=production-google-client-id",
                    "GOOGLE_CLIENT_SECRET=production-google-client-secret",
                    "GITHUB_CLIENT_ID=production-github-client-id",
                    "GITHUB_CLIENT_SECRET=production-github-client-secret"
            );

    @Test
    @DisplayName("prod 프로필은 운영 DB와 인증 secret을 외부 설정에서 주입한다")
    void loadsProductionDatabaseAndAuthenticationSecrets() {
        contextRunner.run(context -> {
            ConfigurableEnvironment environment = context.getEnvironment();

            assertThat(environment.getActiveProfiles()).containsExactly("prod");
            assertThat(environment.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:mysql://production-db:3306/interview_ai");
            assertThat(environment.getProperty("spring.datasource.username"))
                    .isEqualTo("interview_ai");
            assertThat(environment.getProperty("spring.datasource.password"))
                    .isEqualTo("production-password");
            assertThat(environment.getProperty("auth.jwt.secret"))
                    .isEqualTo("production-jwt-secret-that-is-at-least-32-bytes-long");
            assertThat(environment.getProperty(
                    "spring.security.oauth2.client.registration.google.client-id"
            )).isEqualTo("production-google-client-id");
            assertThat(environment.getProperty(
                    "spring.security.oauth2.client.registration.github.client-id"
            )).isEqualTo("production-github-client-id");
        });
    }

    @Test
    @DisplayName("prod 프로필은 SQL 출력을 끄고 proxy와 graceful shutdown을 활성화한다")
    void loadsProductionServerAndLoggingSettings() {
        contextRunner.run(context -> {
            ConfigurableEnvironment environment = context.getEnvironment();

            assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class))
                    .isFalse();
            assertThat(environment.getProperty("server.forward-headers-strategy"))
                    .isEqualTo("native");
            assertThat(environment.getProperty("server.shutdown"))
                    .isEqualTo("graceful");
            assertThat(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
                    .isEqualTo("30s");
            assertThat(environment.getProperty("logging.level.root"))
                    .isEqualTo("INFO");
            assertThat(environment.getProperty("logging.level.org.hibernate.SQL"))
                    .isEqualTo("WARN");
        });
    }

    @Test
    @DisplayName("prod 프로필은 상세 정보 없이 liveness와 readiness probe를 노출한다")
    void loadsProductionHealthProbeSettings() {
        contextRunner.run(context -> {
            ConfigurableEnvironment environment = context.getEnvironment();

            assertThat(environment.getProperty(
                    "management.endpoint.health.probes.enabled",
                    Boolean.class
            )).isTrue();
            assertThat(environment.getProperty("management.endpoint.health.show-details"))
                    .isEqualTo("never");
            assertThat(environment.getProperty(
                    "management.health.livenessstate.enabled",
                    Boolean.class
            )).isTrue();
            assertThat(environment.getProperty(
                    "management.health.readinessstate.enabled",
                    Boolean.class
            )).isTrue();
            assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                    .isEqualTo("health, info");
        });
    }

    @Test
    @DisplayName("prod 프로필에서 필수 DB 비밀번호가 없으면 설정 해석에 실패한다")
    void rejectsMissingProductionDatabasePassword() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "DB_URL=jdbc:mysql://production-db:3306/interview_ai",
                        "DB_USERNAME=interview_ai"
                )
                .run(context -> assertThatThrownBy(
                        () -> context.getEnvironment()
                                .getRequiredProperty("spring.datasource.password")
                )
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("DB_PASSWORD"));
    }
}
