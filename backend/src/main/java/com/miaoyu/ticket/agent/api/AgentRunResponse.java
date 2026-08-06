package com.miaoyu.ticket.agent.api;

import java.time.OffsetDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/** 当前用户可读取的 Agent 运行恢复快照。 */
public record AgentRunResponse(String runId, String sessionId, String status, String planId, Integer planVersion,
        OffsetDateTime startedAt, OffsetDateTime finishedAt, String lastEventId, List<MessageSummary> messages,
        List<StepSummary> steps, List<EventSummary> events) {

    public record MessageSummary(String messageId, String role, String type, String text, OffsetDateTime completedAt) {
    }

    public record StepSummary(String nodeId, String nodeType, String status, int attemptCount, boolean autoSkipped,
            String recoveryHint) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record EventSummary(String eventId, String sessionId, String runId, String planId, Integer planVersion,
            String nodeId,
            String eventType, String displayText, JsonNode payload, OffsetDateTime occurredAt) {
    }
}
