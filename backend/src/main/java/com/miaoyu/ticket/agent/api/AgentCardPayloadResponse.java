package com.miaoyu.ticket.agent.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/** C 可直接渲染的受控卡片载荷；不将持久化 JSON 作为公开 API 类型暴露。 */
@Schema(oneOf = {
        AgentCardPayloadResponse.Question.class,
        AgentCardPayloadResponse.PlanCard.class,
        AgentCardPayloadResponse.TravelAdviceCard.class,
        AgentCardPayloadResponse.BusinessIntent.class,
        AgentCardPayloadResponse.ConfirmationCard.class,
        AgentCardPayloadResponse.Unknown.class
})
public sealed interface AgentCardPayloadResponse permits AgentCardPayloadResponse.Question,
        AgentCardPayloadResponse.PlanCard, AgentCardPayloadResponse.TravelAdviceCard,
        AgentCardPayloadResponse.BusinessIntent,
        AgentCardPayloadResponse.ConfirmationCard, AgentCardPayloadResponse.Unknown {

    static AgentCardPayloadResponse from(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return new Unknown(payload);
        }
        String type = text(payload, "type");
        if ("QUESTION".equals(type)) {
            List<QuestionOption> options = questionOptions(payload.path("options"));
            QuestionInput input = questionInput(payload.path("input"));
            if (hasText(payload, "questionId") && hasText(payload, "questionKind") && hasText(payload, "message")
                    && options != null && input != null && payload.path("allowFreeText").isBoolean()
                    && payload.path("requiresConfirmation").isBoolean() && hasText(payload, "expiresAt")) {
                return new Question(type, text(payload, "questionId"), text(payload, "questionKind"),
                        text(payload, "message"), options, input, payload.path("allowFreeText").asBoolean(),
                        payload.path("requiresConfirmation").asBoolean(), text(payload, "expiresAt"));
            }
        }
        if ("BUSINESS_INTENT".equals(type) && payload.path("payload").isObject()) {
            JsonNode details = payload.path("payload");
            JsonNode reference = details.path("businessRef");
            if (hasText(details, "intent") && hasText(reference, "showId") && hasText(reference, "movieId")
                    && hasText(reference, "cinemaId")) {
                return new BusinessIntent(type, new BusinessIntentDetails(text(details, "intent"),
                        new BusinessReference(text(reference, "showId"), text(reference, "movieId"),
                                text(reference, "cinemaId"))));
            }
        }
        if ("PLAN_CARD".equals(type) && hasText(payload, "actionId")) {
            List<String> displayLines = strings(payload.path("displayLines"));
            List<PlanItem> plans = planItems(payload.path("plans"));
            if (hasText(payload, "actionType") && hasText(payload, "status") && hasText(payload, "expireAt")
                    && displayLines != null && plans != null) {
                return new ConfirmationCard(type, text(payload, "actionId"), text(payload, "actionType"),
                        text(payload, "nodeId"), text(payload, "status"), text(payload, "expireAt"),
                        text(payload, "title"), displayLines, plans, text(payload, "source"), text(payload, "dataAt"),
                        text(payload, "expiresAt"), bool(payload, "degraded"));
            }
        }
        if ("TRAVEL_ADVICE_CARD".equals(type)) {
            TravelWeather weather = travelWeather(payload.path("weather"));
            List<TravelAdviceItem> advice = travelAdvice(payload.path("advice"));
            if (hasText(payload, "taskId") && hasText(payload, "taskStatus") && hasText(payload, "source") && advice != null
                    && payload.path("available").isBoolean() && payload.path("degraded").isBoolean()
                    && payload.path("expired").isBoolean()) {
                return new TravelAdviceCard(type, text(payload, "taskId"), text(payload, "taskStatus"),
                        payload.path("available").asBoolean(), weather, advice, text(payload, "source"), payload.path("degraded").asBoolean(),
                        nullableText(payload, "fallbackType"), nullableText(payload, "dataAt"),
                        nullableText(payload, "expiresAt"), payload.path("expired").asBoolean());
            }
        }
        if ("PLAN_CARD".equals(type)) {
            List<PlanItem> plans = planItems(payload.path("plans"));
            List<String> missingFactors = strings(payload.path("missingFactors"));
            RelaxationSuggestion relaxation = relaxation(payload.path("relaxationSuggestion"));
            if (hasText(payload, "title") && hasText(payload, "schemaVersion")
                    && hasText(payload, "algorithmVersion") && plans != null && missingFactors != null
                    && relaxationValid(payload.path("relaxationSuggestion"), relaxation)
                    && payload.path("usedProfile").isBoolean() && hasText(payload, "source")
                    && hasText(payload, "dataAt") && hasText(payload, "expiresAt")
                    && payload.path("degraded").isBoolean() && payload.path("expired").isBoolean()) {
                return new PlanCard(type, text(payload, "title"), text(payload, "schemaVersion"),
                        text(payload, "algorithmVersion"), plans, missingFactors, relaxation,
                        payload.path("usedProfile").asBoolean(), text(payload, "source"), text(payload, "dataAt"),
                        text(payload, "expiresAt"), payload.path("degraded").asBoolean(),
                        payload.path("expired").asBoolean());
            }
        }
        return new Unknown(payload);
    }

    private static List<QuestionOption> questionOptions(JsonNode value) {
        if (!value.isArray()) {
            return null;
        }
        List<QuestionOption> result = new ArrayList<>();
        for (JsonNode option : value) {
            if (!hasText(option, "optionId") || !hasText(option, "label") || !hasText(option, "value")) {
                return null;
            }
            result.add(new QuestionOption(text(option, "optionId"), text(option, "label"), text(option, "value")));
        }
        return List.copyOf(result);
    }

    private static QuestionInput questionInput(JsonNode value) {
        return hasText(value, "name") && hasText(value, "type")
                ? new QuestionInput(text(value, "name"), text(value, "type")) : null;
    }

    private static List<PlanItem> planItems(JsonNode value) {
        if (!value.isArray()) {
            return null;
        }
        List<PlanItem> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!hasText(item, "planType") || !hasText(item, "movieId") || !hasText(item, "movieName")
                    || !hasText(item, "cinemaId") || !hasText(item, "cinemaName") || !hasText(item, "showId")
                    || !hasText(item, "price") || !hasText(item, "currency") || !hasText(item, "startTime")
                    || strings(item.path("reasons")) == null || !hasText(item, "source")
                    || !hasText(item, "dataAt") || !hasText(item, "expiresAt")
                    || !item.path("expired").isBoolean() || !item.path("purchaseEligible").isBoolean()) {
                return null;
            }
            result.add(new PlanItem(text(item, "planType"), text(item, "movieId"), text(item, "movieName"),
                    text(item, "cinemaId"), text(item, "cinemaName"), text(item, "showId"), text(item, "price"),
                    text(item, "currency"), text(item, "startTime"), nullableText(item, "rating"),
                    number(item, "score"), strings(item.path("reasons")), text(item, "source"), text(item, "dataAt"),
                    text(item, "expiresAt"), item.path("expired").asBoolean(),
                    item.path("purchaseEligible").asBoolean(), integer(item, "distanceMeters")));
        }
        return List.copyOf(result);
    }

    private static List<String> strings(JsonNode value) {
        if (!value.isArray()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                return null;
            }
            result.add(item.asText());
        }
        return List.copyOf(result);
    }

    private static TravelWeather travelWeather(JsonNode value) {
        if (value.isNull()) {
            return null;
        }
        return value.isObject() && value.has("area") && value.has("condition") && value.has("risk")
                        ? new TravelWeather(nullableText(value, "area"), nullableText(value, "condition"),
                                nullableText(value, "risk")) : null;
    }

    private static List<TravelAdviceItem> travelAdvice(JsonNode value) {
        if (!value.isArray()) {
            return null;
        }
        List<TravelAdviceItem> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!hasText(item, "type") || !hasText(item, "text")) {
                return null;
            }
            result.add(new TravelAdviceItem(text(item, "type"), text(item, "text")));
        }
        return List.copyOf(result);
    }

    private static RelaxationSuggestion relaxation(JsonNode value) {
        if (value.isNull()) {
            return null;
        }
        return hasText(value, "factor") && hasText(value, "message")
                ? new RelaxationSuggestion(text(value, "factor"), text(value, "message")) : null;
    }

    private static boolean relaxationValid(JsonNode value, RelaxationSuggestion relaxation) {
        return value.isNull() || relaxation != null;
    }

    private static String text(JsonNode value, String name) {
        return value.path(name).isTextual() ? value.path(name).asText() : null;
    }

    private static String nullableText(JsonNode value, String name) {
        return value.path(name).isNull() ? null : text(value, name);
    }

    private static boolean hasText(JsonNode value, String name) {
        return text(value, name) != null;
    }

    private static Boolean bool(JsonNode value, String name) {
        return value.path(name).isBoolean() ? value.path(name).asBoolean() : null;
    }

    private static Double number(JsonNode value, String name) {
        return value.path(name).isNumber() ? value.path(name).asDouble() : null;
    }

    private static Integer integer(JsonNode value, String name) {
        return value.path(name).canConvertToInt() ? value.path(name).asInt() : null;
    }

    record Question(String type, String questionId, String questionKind, String message,
            List<QuestionOption> options, QuestionInput input, boolean allowFreeText,
            boolean requiresConfirmation, String expiresAt) implements AgentCardPayloadResponse {
    }

    record QuestionOption(String optionId, String label, String value) {
    }

    record QuestionInput(String name, String type) {
    }

    record PlanCard(String type, String title, String schemaVersion, String algorithmVersion, List<PlanItem> plans,
            List<String> missingFactors, RelaxationSuggestion relaxationSuggestion, boolean usedProfile,
            String source, String dataAt, String expiresAt, boolean degraded, boolean expired)
            implements AgentCardPayloadResponse {
    }

    record PlanItem(String planType, String movieId, String movieName, String cinemaId, String cinemaName,
            String showId, String price, String currency, String startTime, String rating, Double score,
            List<String> reasons, String source, String dataAt, String expiresAt, boolean expired,
            boolean purchaseEligible, Integer distanceMeters) {
    }

    record TravelAdviceCard(String type, String taskId, String taskStatus, boolean available, TravelWeather weather,
            List<TravelAdviceItem> advice, String source, boolean degraded, String fallbackType, String dataAt, String expiresAt,
            boolean expired) implements AgentCardPayloadResponse {
    }

    record TravelWeather(String area, String condition, String risk) {
    }

    record TravelAdviceItem(String type, String text) {
    }

    record RelaxationSuggestion(String factor, String message) {
    }

    record BusinessIntent(String type, BusinessIntentDetails payload) implements AgentCardPayloadResponse {
    }

    record BusinessIntentDetails(String intent, BusinessReference businessRef) {
    }

    record BusinessReference(String showId, String movieId, String cinemaId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ConfirmationCard(String type, String actionId, String actionType, String nodeId, String status,
            String expireAt, String title, List<String> displayLines, List<PlanItem> plans, String source,
            String dataAt, String expiresAt, Boolean degraded) implements AgentCardPayloadResponse {
    }

    record Unknown(@JsonValue JsonNode value) implements AgentCardPayloadResponse {
    }
}
