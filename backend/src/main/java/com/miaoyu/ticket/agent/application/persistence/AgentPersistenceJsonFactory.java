package com.miaoyu.ticket.agent.application.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.reply.RecommendationReplyFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationReplyCandidate;
import com.miaoyu.ticket.agent.application.reply.SelectSeatsReplyFacts;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.LinkedHashMap;
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
        if (!(reply.payload() instanceof RecommendationReplyFacts facts)) {
            throw new IllegalArgumentException("只有推荐或选座回复可以生成卡片事件");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        boolean planCard = reply.messageType()
                == com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType.PLAN_CARD;
        payload.put("type", reply.messageType().name());
        payload.put("title", planCard ? "推荐场次" : "影片推荐");
        payload.put(planCard ? "plans" : "movies", facts.candidates().stream()
                .map(AgentPersistenceJsonFactory::cardCandidate)
                .toList());
        payload.put("source", facts.source());
        payload.put("dataAt", facts.dataAt().toString());
        payload.put("expiresAt", facts.expiresAt().toString());
        payload.put("degraded", facts.degraded());
        return write(payload);
    }

    private static Map<String, Object> cardCandidate(RecommendationReplyCandidate candidate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("movieId", candidate.movieId());
        payload.put("cinemaId", candidate.cinemaId());
        payload.put("showId", candidate.showId());
        payload.put("price", candidate.price());
        payload.put("startTime", candidate.startTime() == null ? null : candidate.startTime().toString());
        payload.put("source", candidate.source());
        payload.put("expired", candidate.expired());
        payload.put("purchaseEligible", candidate.purchaseEligible());
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
