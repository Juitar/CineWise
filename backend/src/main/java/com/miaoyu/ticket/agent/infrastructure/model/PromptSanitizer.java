package com.miaoyu.ticket.agent.infrastructure.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** 模型边界的最小脱敏器，不让凭证、邮箱或精确位置原文进入第三方请求。 */
public final class PromptSanitizer {
    private static final String EMAIL = "(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}";
    private static final String BEARER = "(?i)bearer\\s+[a-z0-9._~+/=-]+";
    private static final String JWT = "[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+";
    private static final String COORDINATE = "[-+]?\\d{1,3}\\.\\d{4,},\\s*[-+]?\\d{1,3}\\.\\d{4,}";

    private PromptSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll(BEARER, "[TOKEN]")
                .replaceAll(JWT, "[TOKEN]")
                .replaceAll(EMAIL, "[EMAIL]")
                .replaceAll(COORDINATE, "[LOCATION]");
    }

    public static Map<String, String> sanitizeSlots(Map<String, String> slots) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        slots.forEach((key, value) -> sanitized.put(sanitize(key), sanitize(value)));
        return Map.copyOf(sanitized);
    }
}
