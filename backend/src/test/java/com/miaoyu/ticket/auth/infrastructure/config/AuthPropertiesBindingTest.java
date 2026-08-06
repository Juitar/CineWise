package com.miaoyu.ticket.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AuthPropertiesBindingTest {

    private static final String JWT_SECRET = "binding-test-jwt-secret-at-least-32-bytes";
    private static final ApplicationContextRunner CONTEXT_RUNNER = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "cinewise.auth.jwt-secret=" + JWT_SECRET,
                    "cinewise.auth.audit-hash-secret=binding-test-audit-secret-at-least-32-bytes",
                    "cinewise.auth.access-cookie-name=cinewise_access_token",
                    "cinewise.auth.csrf-cookie-name=cinewise_csrf",
                    "cinewise.auth.csrf-header-name=X-XSRF-TOKEN",
                    "cinewise.auth.cookie-secure=true",
                    "cinewise.auth.cookie-same-site=Lax",
                    "cinewise.auth.login-log-retention-days=30",
                    "cinewise.auth.login-log-cleanup-delay-milliseconds=86400000",
                    "cinewise.auth.demo-seed.enabled=false",
                    "cinewise.auth.demo-seed.user-email=",
                    "cinewise.auth.demo-seed.user-password=",
                    "cinewise.auth.demo-seed.user-nickname=",
                    "cinewise.auth.demo-seed.admin-email=",
                    "cinewise.auth.demo-seed.admin-password=",
                    "cinewise.auth.demo-seed.admin-nickname=",
                    "cinewise.auth.demo-seed.privacy-policy-version=");

    @Test
    void shouldBindValidSessionDurations() {
        CONTEXT_RUNNER
                .withPropertyValues(
                        "cinewise.auth.access-token-ttl=30m",
                        "cinewise.auth.renewal-threshold=10m",
                        "cinewise.auth.absolute-session-ttl=8h")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AuthProperties properties = context.getBean(AuthProperties.class);
                    assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(properties.renewalThreshold()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(properties.absoluteSessionTtl()).isEqualTo(Duration.ofHours(8));
                });
    }

    @ParameterizedTest
    @MethodSource("invalidDurations")
    void shouldFailStartupForInvalidSessionDurations(
            String accessTokenTtl, String renewalThreshold, String absoluteSessionTtl) {
        CONTEXT_RUNNER
                .withPropertyValues(
                        "cinewise.auth.access-token-ttl=" + accessTokenTtl,
                        "cinewise.auth.renewal-threshold=" + renewalThreshold,
                        "cinewise.auth.absolute-session-ttl=" + absoluteSessionTtl)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("cinewise.auth");
                    assertThat(stackMessages(context.getStartupFailure()))
                            .doesNotContain(JWT_SECRET)
                            .containsAnyOf("必须大于零", "必须小于 JWT 有效期");
                });
    }

    private static Stream<Arguments> invalidDurations() {
        return Stream.of(
                Arguments.of("0s", "10m", "8h"),
                Arguments.of("30m", "0s", "8h"),
                Arguments.of("30m", "10m", "0s"),
                Arguments.of("30m", "30m", "8h"),
                Arguments.of("30m", "10m", "20m"));
    }

    private static String stackMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            messages.append(current.getMessage()).append('\n');
        }
        return messages.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthProperties.class)
    static class TestConfiguration {
    }
}
