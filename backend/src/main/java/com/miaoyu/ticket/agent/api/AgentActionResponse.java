package com.miaoyu.ticket.agent.api;

import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationCardStatus;
import java.time.OffsetDateTime;

/** 确认 REST 成功响应，不包含订单、工具参数或稳定写标识。 */
public record AgentActionResponse(
        String actionId,
        String runId,
        int planVersion,
        AgentConfirmationCardStatus status,
        OffsetDateTime updatedAt) {
}
