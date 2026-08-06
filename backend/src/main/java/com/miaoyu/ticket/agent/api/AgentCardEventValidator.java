package com.miaoyu.ticket.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * C 消费卡片前使用的固定协议校验器。
 *
 * <p>它只接受白名单卡片和纯 JSON 字段，不把模型文本、HTML 或任意对象传给前端组件。未知事件和未知
 * payload 都返回安全文本结果；已知事件缺少必填字段则拒绝，避免错误投影污染续传游标。
 */
public final class AgentCardEventValidator {
    private static final List<String> CARD_TYPES =
            List.of("TEXT", "QUESTION", "MOVIE_CARD", "PLAN_CARD", "BUSINESS_INTENT", "PROGRESS", "ERROR");

    private AgentCardEventValidator() {
    }

    public static ValidationResult validate(JsonNode event) {
        if (!isObject(event)) {
            return rejected("事件必须是对象");
        }
        if (!text(event, "eventId") || !event.path("eventId").asText().matches("[1-9][0-9]*")
                || !text(event, "eventType") || !text(event, "sessionId") || !text(event, "runId")
                || !text(event, "displayText") || !isObject(event.path("payload")) || !hasNullableText(event, "planId")
                || !hasNullableInteger(event, "planVersion") || !hasNullableText(event, "nodeId")
                || !hasNullableTime(event, "occurredAt")) {
            return rejected("卡片事件外层字段无效");
        }
        if (!"card".equals(event.path("eventType").asText())) {
            return safeText("未知事件类型");
        }
        JsonNode payload = event.path("payload");
        if (!text(payload, "type")) {
            return rejected("payload.type 不能为空");
        }
        String type = payload.path("type").asText();
        if (!CARD_TYPES.contains(type)) {
            return safeText("未知卡片类型");
        }
        return switch (type) {
            case "TEXT" -> required(payload, "text") ? render(type) : rejected("TEXT.text 不能为空");
            case "QUESTION" -> question(payload);
            case "MOVIE_CARD" -> movieCard(payload);
            case "PLAN_CARD" -> planCard(event, payload);
            case "BUSINESS_INTENT" -> businessIntent(event, payload);
            case "PROGRESS" -> required(payload, "stage") && required(payload, "status")
                    ? render(type) : rejected("PROGRESS 缺少 stage 或 status");
            case "ERROR" -> required(payload, "code") && required(payload, "message")
                    && payload.path("retryable").isBoolean()
                    ? render(type) : rejected("ERROR 字段无效");
            default -> safeText("未知卡片类型");
        };
    }

    private static ValidationResult question(JsonNode payload) {
        if (!required(payload, "questionId") || !required(payload, "questionKind") || !required(payload, "message")
                || !payload.path("allowFreeText").isBoolean() || !payload.path("requiresConfirmation").isBoolean()
                || !time(payload, "expiresAt") || !payload.path("options").isArray()) {
            return rejected("QUESTION 外层字段无效");
        }
        if (payload.path("allowFreeText").asBoolean() && (!isObject(payload.path("input"))
                || !required(payload.path("input"), "name") || !required(payload.path("input"), "type"))) {
            return rejected("QUESTION 自由输入字段无效");
        }
        if ("LOCATION_PERMISSION".equals(payload.path("questionKind").asText())) {
            JsonNode authorization = payload.path("locationAuthorization");
            if (!isValidLocationAuthorization(authorization)) {
                return rejected("位置授权 QUESTION 字段无效");
            }
        }
        return render("QUESTION");
    }

    private static ValidationResult movieCard(JsonNode payload) {
        return required(payload, "title") && payload.path("movies").isArray() && required(payload, "source")
                && time(payload, "dataAt") && time(payload, "expiresAt") && payload.path("degraded").isBoolean()
                ? render("MOVIE_CARD") : rejected("MOVIE_CARD 字段无效");
    }

    private static ValidationResult planCard(JsonNode event, JsonNode payload) {
        return hasPlanContext(event) && required(payload, "title") && payload.path("plans").isArray()
                && required(payload, "source")
                && time(payload, "dataAt") && time(payload, "expiresAt") && payload.path("degraded").isBoolean()
                ? render("PLAN_CARD") : rejected("PLAN_CARD 字段无效");
    }

    private static ValidationResult businessIntent(JsonNode event, JsonNode payload) {
        JsonNode nested = payload.path("payload");
        JsonNode businessRef = nested.path("businessRef");
        return hasPlanContext(event) && "SELECT_SEATS".equals(nested.path("intent").asText())
                && required(businessRef, "showId")
                ? render("BUSINESS_INTENT") : rejected("SELECT_SEATS 卡片字段无效");
    }

    /** 计划卡片只有携带当前计划标识和正版本号时，C 才能安全处理旧版本和续传。 */
    private static boolean hasPlanContext(JsonNode event) {
        return text(event, "planId") && event.path("planVersion").isInt() && event.path("planVersion").asInt() > 0;
    }

    /** 位置授权字段是前端权限流程的固定协议，不能把任意文本当作可执行状态。 */
    private static boolean isValidLocationAuthorization(JsonNode authorization) {
        return isObject(authorization) && "DEVICE_LOCATION".equals(authorization.path("permission").asText())
                && List.of("NOT_REQUESTED", "GRANTED", "DENIED", "EXPIRED")
                        .contains(authorization.path("authorizationState").asText())
                && "ROUTE_PLANNING".equals(authorization.path("purpose").asText())
                && authorization.path("resubmittable").isBoolean() && required(authorization, "deniedAction")
                && required(authorization, "expiredAction");
    }

    private static boolean isObject(JsonNode node) {
        return node != null && node.isObject();
    }

    private static boolean text(JsonNode node, String field) {
        return node.path(field).isTextual() && !node.path(field).asText().isBlank();
    }

    private static boolean required(JsonNode node, String field) {
        return text(node, field);
    }

    private static boolean hasNullableText(JsonNode node, String field) {
        return node.has(field) && (node.path(field).isNull() || text(node, field));
    }

    private static boolean hasNullableInteger(JsonNode node, String field) {
        return node.has(field) && (node.path(field).isNull() || node.path(field).isInt());
    }

    private static boolean hasNullableTime(JsonNode node, String field) {
        return node.has(field) && (node.path(field).isNull() || time(node, field));
    }

    private static boolean time(JsonNode node, String field) {
        if (!text(node, field)) {
            return false;
        }
        try {
            OffsetDateTime.parse(node.path(field).asText());
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static ValidationResult render(String type) {
        return new ValidationResult(Decision.RENDER, type, null);
    }

    private static ValidationResult safeText(String reason) {
        return new ValidationResult(Decision.SAFE_TEXT, null, reason);
    }

    private static ValidationResult rejected(String reason) {
        return new ValidationResult(Decision.REJECT, null, reason);
    }

    public enum Decision {
        RENDER, SAFE_TEXT, REJECT
    }

    public record ValidationResult(Decision decision, String payloadType, String reason) {
    }
}
