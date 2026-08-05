package com.miaoyu.ticket.agent.infrastructure.persistence;

import java.time.LocalDateTime;

/** `agent_action` 的 MyBatis 行对象；Command 保持 JSON，不穿透基础设施层。 */
public record AgentConfirmationActionEntity(
        long id,
        String actionId,
        long userId,
        long agentSessionId,
        long agentRunId,
        String runId,
        String planId,
        int planVersion,
        String nodeId,
        String toolName,
        String commandSnapshot,
        String parameterHashVersion,
        String parameterHash,
        LocalDateTime expireAt,
        String status,
        String clientRequestId,
        String idempotencyKey,
        String resultReference,
        String recoveryHint,
        LocalDateTime resultUnknownAt,
        LocalDateTime recoveryUntil,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
