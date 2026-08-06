package com.miaoyu.ticket.auth.infrastructure.mail;

import com.miaoyu.ticket.auth.application.mail.EmailTemplateRegistry;
import com.miaoyu.ticket.auth.infrastructure.config.EmailDeliveryProperties;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 只登记认证设计确认的提醒变量；业务文案由部署配置提供，不写入 C 的代码。 */
@Component
public class ConfiguredEmailTemplateRegistry implements EmailTemplateRegistry {

    private static final String VIEWING_REMINDER = "VIEWING_REMINDER";
    private static final Set<String> VARIABLES = Set.of(
            "movieTitle", "cinemaName", "startAt", "adviceSummary", "relativePath");
    private static final Pattern RELATIVE_PATH = Pattern.compile("/travel/[1-9]\\d{0,18}");
    private static final int MAX_VALUE_LENGTH = 200;
    private static final int MAX_TOTAL_LENGTH = 600;

    private final EmailDeliveryProperties properties;

    public ConfiguredEmailTemplateRegistry(EmailDeliveryProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<RenderedEmail> render(String templateCode, Map<String, String> variables) {
        if (!VIEWING_REMINDER.equals(templateCode)
                || variables == null
                || !variables.keySet().equals(VARIABLES)
                || !isConfigured()) {
            return Optional.empty();
        }
        int totalLength = 0;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank() || value.length() > MAX_VALUE_LENGTH) {
                return Optional.empty();
            }
            totalLength += value.length();
        }
        if (totalLength > MAX_TOTAL_LENGTH
                || !RELATIVE_PATH.matcher(variables.get("relativePath")).matches()) {
            return Optional.empty();
        }

        EmailDeliveryProperties.Template template = properties.viewingReminder();
        String subject = renderTemplate(template.subject(), variables);
        String body = renderTemplate(template.body(), variables);
        return Optional.of(new RenderedEmail(subject, body));
    }

    private boolean isConfigured() {
        EmailDeliveryProperties.Template template = properties.viewingReminder();
        return properties.enabled()
                && properties.from() != null
                && !properties.from().isBlank()
                && template.subject() != null
                && !template.subject().isBlank()
                && template.body() != null
                && !template.body().isBlank();
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        String rendered = template;
        for (String name : VARIABLES) {
            rendered = rendered.replace("{" + name + "}", escape(variables.get(name)));
        }
        return rendered;
    }

    private String escape(String value) {
        return value.replace("&", "＆")
                .replace("<", "＜")
                .replace(">", "＞")
                .replace("\r", " ");
    }
}
