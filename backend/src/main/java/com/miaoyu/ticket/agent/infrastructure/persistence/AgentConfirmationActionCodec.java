package com.miaoyu.ticket.agent.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;

/** 仅编码服务端受控的确认建单 Command，拒绝读取额外业务字段。 */
final class AgentConfirmationActionCodec {
    private final ObjectMapper objectMapper;

    AgentConfirmationActionCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String write(ConfirmedOrderCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("确认动作 Command 序列化失败", exception);
        }
    }

    ConfirmedOrderCommand read(String snapshot) {
        try {
            JsonNode root = objectMapper.readTree(snapshot);
            String payload = root.isTextual() ? root.textValue() : snapshot;
            return objectMapper.readValue(payload, ConfirmedOrderCommand.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("确认动作 Command 快照不可解析", exception);
        }
    }
}
