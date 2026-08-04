package com.miaoyu.ticket.agent.infrastructure.persistence;

import java.time.LocalDateTime;

/** `agent_message` 的持久化行；payloadJson 只可保存受控展示事实。 */
public record AgentMessageEntity(
        long id,
        String messageId,
        long sessionId,
        long runId,
        long userId,
        String role,
        String messageType,
        String text,
        String payloadJson,
        String status,
        LocalDateTime completedAt,
        LocalDateTime createTime,
        LocalDateTime expireAt) {
}
