package com.miaoyu.ticket.agent.infrastructure.persistence;

import java.time.LocalDateTime;

/** `agent_event` 的追加记录行。 */
public record AgentRuntimeEventEntity(
        long eventId,
        String sessionId,
        String runId,
        String eventType,
        String payloadJson,
        LocalDateTime expireAt,
        LocalDateTime createTime) {
}
