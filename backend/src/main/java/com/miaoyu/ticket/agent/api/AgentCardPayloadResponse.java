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
        // 持久化载荷可能来自历史版本，解析失败必须降级为 Unknown，不能把未校验字段下发给前端。
        if (payload == null || !payload.isObject()) {
            return new Unknown(payload);
        }
        String type = text(payload, "type");
        if ("QUESTION".equals(type)) {
            // 问题卡缺少任一必填字段时不创建半成品卡，前端只渲染完整的可交互结构。
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
            // 业务跳转只认展示所需的三个公开引用，禁止从 JSON 透传内部数据库主键。
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
            // 有 actionId 的 PLAN_CARD 是确认卡，必须先于普通推荐卡判断。
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
            // 出行建议允许 weather 为 null，但 available 为 true 时必须说明数据来源。
            TravelWeather weather = travelWeather(payload.path("weather"));
            List<TravelAdviceItem> advice = travelAdvice(payload.path("advice"));
            boolean available = payload.path("available").isBoolean() && payload.path("available").asBoolean();
            if (hasText(payload, "taskId") && hasText(payload, "taskStatus") && hasNullableText(payload, "source")
                    && (!available || hasText(payload, "source")) && advice != null
                    && payload.path("available").isBoolean() && payload.path("degraded").isBoolean()
                    && payload.path("expired").isBoolean()) {
                return new TravelAdviceCard(type, text(payload, "taskId"), text(payload, "taskStatus"),
                        available, weather, advice, nullableText(payload, "source"),
                        payload.path("degraded").asBoolean(),
                        nullableText(payload, "fallbackType"), nullableText(payload, "dataAt"),
                        nullableText(payload, "expiresAt"), payload.path("expired").asBoolean());
            }
        }
        if ("PLAN_CARD".equals(type)) {
            // 普通推荐卡只有所有计划项和时效字段有效时才可恢复。
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
        // 选项数组任意一项非法即整体拒绝，避免前端把错误选项提交为有效槽位值。
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
        // 输入定义只暴露 name/type 白名单，具体校验仍由后端回复入口执行。
        return hasText(value, "name") && hasText(value, "type")
                ? new QuestionInput(text(value, "name"), text(value, "type")) : null;
    }

    private static List<PlanItem> planItems(JsonNode value) {
        // 计划项必须完整校验；推荐卡不能依赖前端猜测影院、电影或场次信息。
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
        // 不接受混合数组，保证前端渲染原因和缺失因素时无需处理非文本值。
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
        // 天气字段可为空，空值表示上游不可用而不是构造假的天气结果。
        if (value.isNull()) {
            return null;
        }
        return value.isObject() && value.has("area") && value.has("condition") && value.has("risk")
                        ? new TravelWeather(nullableText(value, "area"), nullableText(value, "condition"),
                                nullableText(value, "risk")) : null;
    }

    private static List<TravelAdviceItem> travelAdvice(JsonNode value) {
        // 建议项限定 type/text 两个展示字段，避免历史 JSON 带入未定义结构。
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
        // 放宽建议为可选内容，null 代表本次没有放宽推荐条件。
        if (value.isNull()) {
            return null;
        }
        return hasText(value, "factor") && hasText(value, "message")
                ? new RelaxationSuggestion(text(value, "factor"), text(value, "message")) : null;
    }

    private static boolean relaxationValid(JsonNode value, RelaxationSuggestion relaxation) {
        // JSON 显式 null 与缺失后解析出的 null 都按无放宽建议处理。
        return value.isNull() || relaxation != null;
    }

    private static String text(JsonNode value, String name) {
        return value.path(name).isTextual() ? value.path(name).asText() : null;
    }

    private static String nullableText(JsonNode value, String name) {
        // 只有 JSON 明确为 null 才返回 null；其他非文本值交给 text 判为无效。
        return value.path(name).isNull() ? null : text(value, name);
    }

    private static boolean hasText(JsonNode value, String name) {
        return text(value, name) != null;
    }

    private static boolean hasNullableText(JsonNode value, String name) {
        return value.has(name) && (value.path(name).isNull() || hasText(value, name));
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

    /** 问题选项的 value 是后端槽位值，label 仅供界面展示，二者不能混用。 */
    record QuestionOption(String optionId, String label, String value) {
    }

    record QuestionInput(String name, String type) {
    }

    record PlanCard(String type, String title, String schemaVersion, String algorithmVersion, List<PlanItem> plans,
            List<String> missingFactors, RelaxationSuggestion relaxationSuggestion, boolean usedProfile,
            String source, String dataAt, String expiresAt, boolean degraded, boolean expired)
            implements AgentCardPayloadResponse {
    }

    /** 单个推荐计划保存公开内容 ID、展示文本、时效和是否允许购票，不保存内部订单数据。 */
    record PlanItem(String planType, String movieId, String movieName, String cinemaId, String cinemaName,
            String showId, String price, String currency, String startTime, String rating, Double score,
            List<String> reasons, String source, String dataAt, String expiresAt, boolean expired,
            boolean purchaseEligible, Integer distanceMeters) {
    }

    /** 出行卡可以是降级结果；available 与 expired 由服务端决定，前端不能自行改写。 */
    record TravelAdviceCard(String type, String taskId, String taskStatus, boolean available, TravelWeather weather,
            List<TravelAdviceItem> advice, String source, boolean degraded, String fallbackType, String dataAt,
            String expiresAt,
            boolean expired) implements AgentCardPayloadResponse {
    }

    record TravelWeather(String area, String condition, String risk) {
    }

    record TravelAdviceItem(String type, String text) {
    }

    record RelaxationSuggestion(String factor, String message) {
    }

    /** 业务意图卡只携带跳转引用，不应被当作普通聊天文本重新送入模型。 */
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

    /** 无法识别的历史载荷保留原始 JSON 供兼容处理，但调用方不得按业务卡渲染。 */
    record Unknown(@JsonValue JsonNode value) implements AgentCardPayloadResponse {
    }
}
