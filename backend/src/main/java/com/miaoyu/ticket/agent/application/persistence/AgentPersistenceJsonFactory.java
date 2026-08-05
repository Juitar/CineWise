package com.miaoyu.ticket.agent.application.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
