package com.miaoyu.ticket.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

class SmtpEnvironmentBindingTest {

    @Test
    void shouldBindSmtpSslAndConnectionEnvironmentToRealApplicationYaml() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "test-smtp-environment",
                Map.of(
                        "SMTP_SSL_ENABLED", "true",
                        "SMTP_STARTTLS_ENABLED", "false",
                        "SMTP_STARTTLS_REQUIRED", "false",
                        "SMTP_TEST_CONNECTION", "true")));

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("application-yaml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.ssl.enable"))
                .isEqualTo("true");
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable"))
                .isEqualTo("false");
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.starttls.required"))
                .isEqualTo("false");
        assertThat(environment.getProperty("spring.mail.test-connection")).isEqualTo("true");
    }
}
