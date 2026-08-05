package com.miaoyu.ticket.agent.api;

import java.time.OffsetDateTime;

/** 当前用户可见的会话摘要，不暴露内部用户 ID 或活动运行内部 ID。 */
public record AgentSessionResponse(
        String sessionId, String summary, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
