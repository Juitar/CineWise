package com.miaoyu.ticket.agent.application.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardItem;
import com.miaoyu.ticket.agent.application.reply.SelectSeatsReplyFacts;
import com.miaoyu.ticket.agent.application.reply.QuestionReplyFacts;
import com.miaoyu.ticket.agent.application.reply.TravelAdviceCardFacts;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 将已经校验并收窄的 Agent 事实转为可存储 JSON，禁止传入工具原始结果。 */
@Component
public class AgentPersistenceJsonFactory {
    private final ObjectMapper objectMapper;

    public AgentPersistenceJsonFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 保存已校验的依赖节点 ID。 */
    public AgentStoredJson dependencies(ExecutionPlanNode node) {
        return write(node.dependsOn());
    }

    /** 保存已校验的输入来源，不保存输入值以外的模型原始内容。 */
    public AgentStoredJson inputReferences(ExecutionPlanNode node) {
        return write(node.inputRefs());
    }

    /** 只保留本节点已引用的槽位值，避免把未引用的会话数据或精确位置写入运行步骤。 */
    public AgentStoredJson slotSnapshot(ExecutionPlanNode node) {
        Map<String, String> values = new TreeMap<>();
        node.inputRefs().stream()
                .filter(reference -> reference.source() == InputReferenceSource.SLOT)
                .forEach(reference -> {
                    String value = node.slotSnapshot().values().get(reference.sourceId());
                    if (value != null) {
                        values.put(reference.inputName(), value);
                    }
                });
        return write(new PersistedSlotSnapshot(node.slotSnapshot().version(), values));
    }

    /** 回复 payload 已由受控类型生成，保存时不接收模型原始输入或完整工具响应。 */
    public AgentStoredJson replyPayload(ReplyGenerationResponse reply) {
        return write(reply.payload());
    }

    /** C 的卡片事件只携带已收窄的推荐事实；不写工具原始响应、座位或确认参数。 */
    public AgentStoredJson cardPayload(ReplyGenerationResponse reply) {
        return cardPayload(reply, null);
    }

    /** QUESTION 也必须以既有 card SSE 协议输出完整可渲染载荷。 */
    public AgentStoredJson cardPayload(ReplyGenerationResponse reply, LocalDateTime occurredAt) {
        if (reply.payload() instanceof QuestionReplyFacts facts) {
            if (occurredAt == null) {
                throw new IllegalArgumentException("QUESTION 卡片必须提供发生时间");
            }
            OffsetDateTime expiresAt = occurredAt.plusMinutes(10L)
                    .atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
            Map<String, Object> input = Map.of("name", facts.inputLabel(), "type", "TEXT");
            return write(Map.of(
                    "type", "QUESTION",
                    "questionId", UUID.randomUUID().toString(),
                    "questionKind", facts.kind().name(),
                    "message", reply.text(),
                    "options", List.of(),
                    "allowFreeText", true,
                    "input", input,
                    "requiresConfirmation", false,
                    "expiresAt", expiresAt.toString()));
        }
        if (reply.payload() instanceof SelectSeatsReplyFacts) {
            SelectSeatsReplyFacts facts = (SelectSeatsReplyFacts) reply.payload();
            return write(Map.of(
                    "type", "BUSINESS_INTENT",
                    "payload", Map.of(
                            "intent", "SELECT_SEATS",
                            "businessRef", Map.of(
                                    "showId", facts.showId(),
                                    "movieId", facts.movieId(),
                                    "cinemaId", facts.cinemaId()))));
        }
        if (reply.payload() instanceof TravelAdviceCardFacts facts) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "TRAVEL_ADVICE_CARD");
            payload.put("taskId", facts.taskId());
            payload.put("taskStatus", facts.taskStatus());
            payload.put("available", facts.available());
            payload.put("weather", facts.weather());
            payload.put("advice", facts.advice());
            payload.put("source", facts.source());
            payload.put("degraded", facts.degraded());
            payload.put("fallbackType", facts.fallbackType());
            payload.put("dataAt", facts.dataAt() == null ? null : facts.dataAt().toString());
            payload.put("expiresAt", facts.expiresAt() == null ? null : facts.expiresAt().toString());
            payload.put("expired", facts.expired());
            return write(payload);
        }
        if (!(reply.payload() instanceof RecommendationPlanCardFacts facts)) {
            throw new IllegalArgumentException("只有完整推荐或选座回复可以生成卡片事件");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "PLAN_CARD");
        payload.put("title", "推荐场次");
        payload.put("schemaVersion", facts.schemaVersion());
        payload.put("algorithmVersion", facts.algorithmVersion());
        payload.put("plans", facts.plans().stream()
                .map(AgentPersistenceJsonFactory::cardPlan)
                .toList());
        payload.put("missingFactors", facts.missingFactors());
        payload.put("relaxationSuggestion", facts.relaxationSuggestion());
        payload.put("usedProfile", facts.usedProfile());
        payload.put("source", facts.source());
        payload.put("dataAt", facts.dataAt().toString());
        payload.put("expiresAt", facts.expiresAt().toString());
        payload.put("degraded", facts.degraded());
        payload.put("expired", facts.expired());
        return write(payload);
    }

    private static Map<String, Object> cardPlan(RecommendationPlanCardItem plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planType", plan.planType());
        payload.put("movieId", plan.movieId());
        payload.put("movieName", plan.movieName());
        payload.put("cinemaId", plan.cinemaId());
        payload.put("cinemaName", plan.cinemaName());
        payload.put("showId", plan.showId());
        payload.put("price", plan.price());
        payload.put("currency", plan.currency());
        payload.put("startTime", plan.startTime().toString());
        payload.put("rating", plan.rating());
        payload.put("score", plan.score());
        payload.put("reasons", plan.reasons());
        payload.put("source", plan.source());
        payload.put("dataAt", plan.dataAt().toString());
        payload.put("expiresAt", plan.expiresAt().toString());
        payload.put("expired", plan.expired());
        payload.put("purchaseEligible", plan.purchaseEligible());
        payload.put("distanceMeters", plan.distanceMeters());
        return payload;
    }

    /** 将固定事件摘要序列化为 JSON 对象，避免手工拼接字符串破坏事件载荷。 */
    public AgentStoredJson eventPayload(Object value) {
        return write(value);
    }

    private AgentStoredJson write(Object value) {
        try {
            return new AgentStoredJson(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 持久化 JSON 序列化失败", exception);
        }
    }

    private record PersistedSlotSnapshot(long version, Map<String, String> values) {
        private PersistedSlotSnapshot {
            values = Map.copyOf(values);
        }
    }
}
