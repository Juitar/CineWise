package com.miaoyu.ticket.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.infrastructure.config.EmailDeliveryProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfiguredEmailTemplateRegistryTest {

    private final ConfiguredEmailTemplateRegistry registry = new ConfiguredEmailTemplateRegistry(
            new EmailDeliveryProperties(
                    true,
                    "no-reply@cinewise.test",
                    new EmailDeliveryProperties.Template(
                            "观影提醒：{movieTitle}",
                            "影院 {cinemaName}，时间 {startAt}，建议 {adviceSummary}，查看 {relativePath}")));

    @Test
    void shouldRenderOnlyWhitelistedVariablesAndEscapeText() {
        Map<String, String> variables = variables();
        variables.put("movieTitle", "<测试影片>");

        assertThat(registry.render("VIEWING_REMINDER", variables))
                .hasValueSatisfying(rendered -> {
                    assertThat(rendered.subject()).contains("＜测试影片＞").doesNotContain("<测试影片>");
                    assertThat(rendered.body()).contains("/travel/1001");
                });
    }

    @Test
    void shouldRejectUnknownSensitiveMissingAndOverlongVariables() {
        assertThat(registry.render("UNKNOWN", variables())).isEmpty();

        Map<String, String> sensitive = variables();
        sensitive.put("token", "secret");
        assertThat(registry.render("VIEWING_REMINDER", sensitive)).isEmpty();

        Map<String, String> missing = variables();
        missing.remove("movieTitle");
        assertThat(registry.render("VIEWING_REMINDER", missing)).isEmpty();

        Map<String, String> overlong = variables();
        overlong.put("adviceSummary", "a".repeat(201));
        assertThat(registry.render("VIEWING_REMINDER", overlong)).isEmpty();
    }

    @Test
    void shouldRejectExternalOrMalformedTravelPath() {
        Map<String, String> variables = variables();
        variables.put("relativePath", "https://example.com/travel/1001");
        assertThat(registry.render("VIEWING_REMINDER", variables)).isEmpty();

        variables.put("relativePath", "/travel/not-a-number");
        assertThat(registry.render("VIEWING_REMINDER", variables)).isEmpty();
    }

    @Test
    void shouldFailClosedWhenTemplateContentIsNotConfigured() {
        ConfiguredEmailTemplateRegistry disabled = new ConfiguredEmailTemplateRegistry(
                new EmailDeliveryProperties(false, "", new EmailDeliveryProperties.Template("", "")));
        assertThat(disabled.render("VIEWING_REMINDER", variables())).isEmpty();
    }

    private Map<String, String> variables() {
        Map<String, String> values = new HashMap<>();
        values.put("movieTitle", "测试影片");
        values.put("cinemaName", "测试影院");
        values.put("startAt", "2026-08-06T20:00:00+08:00");
        values.put("adviceSummary", "请提前出发");
        values.put("relativePath", "/travel/1001");
        return values;
    }
}
