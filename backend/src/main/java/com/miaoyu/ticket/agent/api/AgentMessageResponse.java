package com.miaoyu.ticket.agent.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/** 历史消息仅输出前端可展示的受控字段。 */
public record AgentMessageResponse(String messageId, String role, String type, String text,
        @JsonInclude(JsonInclude.Include.ALWAYS) JsonNode payload, String status, OffsetDateTime completedAt,
        OffsetDateTime createdAt) {
}
