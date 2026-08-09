package com.miaoyu.ticket.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigurationTest {

    @Test
    void shouldBindCommaSeparatedOriginsFromEnvironmentProperty() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "cinewise.security.cors.allowed-origins",
                "https://47.97.45.119,http://localhost:8000"));

        CorsProperties properties = new Binder(source)
                .bind("cinewise.security.cors", Bindable.of(CorsProperties.class))
                .orElseThrow(() -> new AssertionError("CORS 配置绑定失败"));

        assertThat(properties.allowedOrigins())
                .containsExactly("https://47.97.45.119", "http://localhost:8000");
    }

    @Test
    void shouldAllowConfiguredHttpsDemoOrigin() {
        CorsConfigurationSource source = new CorsConfiguration().corsConfigurationSource(
                new CorsProperties(List.of("https://47.97.45.119")));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login/password");
        request.addHeader("Origin", "https://47.97.45.119");

        org.springframework.web.cors.CorsConfiguration configuration =
                source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://47.97.45.119"))
                .isEqualTo("https://47.97.45.119");
    }

    @Test
    void shouldRejectUnconfiguredOrigin() {
        CorsConfigurationSource source = new CorsConfiguration().corsConfigurationSource(
                new CorsProperties(List.of("https://47.97.45.119")));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login/password");
        request.addHeader("Origin", "https://attacker.example");

        org.springframework.web.cors.CorsConfiguration configuration =
                source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://attacker.example")).isNull();
    }
}
