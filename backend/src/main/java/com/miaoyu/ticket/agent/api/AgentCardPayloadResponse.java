package com.miaoyu.ticket.agent.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/** C 可直接渲染的受控卡片载荷；不将持久化 JSON 作为公开 API 类型暴露。 */
@Schema(oneOf = {
        AgentCardPayloadResponse.Question.class,
        AgentCardPayloadResponse.PlanCard.class,
        AgentCardPayloadResponse.BusinessIntent.class,
        AgentCardPayloadResponse.ConfirmationCard.class,
        AgentCardPayloadResponse.Unknown.class
})
public sealed interface AgentCardPayloadResponse permits AgentCardPayloadResponse.Question,
        AgentCardPayloadResponse.PlanCard, AgentCardPayloadResponse.BusinessIntent,
        AgentCardPayloadResponse.ConfirmationCard, AgentCardPayloadResponse.Unknown {

    static AgentCardPayloadResponse from(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return new Unknown(payload);
        }
        String type = text(payload, "type");
        if ("QUESTION".equals(type) && hasText(payload, "questionId") && hasText(payload, "questionKind")
                && hasText(payload, "message") && payload.path("options").isArray() && payload.path("input").isObject()
                && payload.path("allowFreeText").isBoolean() && payload.path("requiresConfirmation").isBoolean()
                && hasText(payload, "expiresAt")) {
            return new Question(type, text(payload, "questionId"), text(payload, "questionKind"),
                    text(payload, "message"), payload.path("options"), payload.path("input"),
                    payload.path("allowFreeText").asBoolean(), payload.path("requiresConfirmation").asBoolean(),
                    text(payload, "expiresAt"));
        }
        if ("BUSINESS_INTENT".equals(type) && payload.path("payload").isObject()
                && hasText(payload.path("payload"), "intent")
                && payload.path("payload").path("businessRef").isObject()) {
            JsonNode details = payload.path("payload");
            JsonNode reference = details.path("businessRef");
            if (hasText(reference, "showId") && hasText(reference, "movieId") && hasText(reference, "cinemaId")) {
                return new BusinessIntent(type, new BusinessIntentDetails(text(details, "intent"),
                        new BusinessReference(text(reference, "showId"), text(reference, "movieId"),
                                text(reference, "cinemaId"))));
            }
        }
        if ("PLAN_CARD".equals(type) && hasText(payload, "actionId") && hasText(payload, "actionType")
                && hasText(payload, "status") && hasText(payload, "expireAt")) {
            return new ConfirmationCard(type, text(payload, "actionId"), text(payload, "actionType"),
                    text(payload, "nodeId"), text(payload, "status"), text(payload, "expireAt"), text(payload, "title"),
                    payload.path("displayLines"), payload.path("plans"), text(payload, "source"),
                    text(payload, "dataAt"),
                    text(payload, "expiresAt"), booleanValue(payload, "degraded"));
        }
        if ("PLAN_CARD".equals(type) && hasText(payload, "title") && payload.path("plans").isArray()
                && hasText(payload, "source") && hasText(payload, "dataAt") && hasText(payload, "expiresAt")) {
            return new PlanCard(type, text(payload, "title"), integerValue(payload, "schemaVersion"),
                    text(payload, "algorithmVersion"), payload.path("plans"), optionalNode(payload, "missingFactors"),
                    text(payload, "relaxationSuggestion"), booleanValue(payload, "usedProfile"),
                    text(payload, "source"),
                    text(payload, "dataAt"), text(payload, "expiresAt"), booleanValue(payload, "degraded"),
                    booleanValue(payload, "expired"));
        }
        return new Unknown(payload);
    }

    private static String text(JsonNode value, String name) {
        return value.path(name).isTextual() ? value.path(name).asText() : null;
    }

    private static boolean hasText(JsonNode value, String name) {
        return text(value, name) != null;
    }

    private static Boolean booleanValue(JsonNode value, String name) {
        return value.path(name).isBoolean() ? value.path(name).asBoolean() : null;
    }

    private static Integer integerValue(JsonNode value, String name) {
        return value.path(name).canConvertToInt() ? value.path(name).asInt() : null;
    }

    private static JsonNode optionalNode(JsonNode value, String name) {
        return value.has(name) ? value.path(name) : null;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Question(String type, String questionId, String questionKind, String message, JsonNode options,
            JsonNode input, boolean allowFreeText, boolean requiresConfirmation, String expiresAt)
            implements AgentCardPayloadResponse {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PlanCard(String type, String title, Integer schemaVersion, String algorithmVersion, JsonNode plans,
            JsonNode missingFactors, String relaxationSuggestion, Boolean usedProfile, String source, String dataAt,
            String expiresAt, Boolean degraded, Boolean expired) implements AgentCardPayloadResponse {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BusinessIntent(String type, BusinessIntentDetails payload) implements AgentCardPayloadResponse {
    }

    record BusinessIntentDetails(String intent, BusinessReference businessRef) {
    }

    record BusinessReference(String showId, String movieId, String cinemaId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ConfirmationCard(String type, String actionId, String actionType, String nodeId, String status,
            String expireAt, String title, JsonNode displayLines, JsonNode plans, String source, String dataAt,
            String expiresAt, Boolean degraded) implements AgentCardPayloadResponse {
    }

    record Unknown(@JsonValue JsonNode value) implements AgentCardPayloadResponse {
    }
}
