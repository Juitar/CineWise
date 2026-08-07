package com.miaoyu.ticket.agent.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/** C 的 Agent 卡片事件 DTO；保留既有 SSE 外层字段和受控 payload 原样。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AgentCardEventResponse(
        String eventId,
        String sessionId,
        String runId,
        String planId,
        Integer planVersion,
        String nodeId,
        String eventType,
        String displayText,
        JsonNode payload,
        OffsetDateTime occurredAt) {
}
