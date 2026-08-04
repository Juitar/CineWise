package com.miaoyu.ticket.agent.infrastructure.persistence;

import java.time.LocalDateTime;

/** `agent_run` 的持久化行；请求摘要只保存版本和小写 SHA-256 值。 */
public record AgentRunEntity(
        long id,
        String runId,
        long sessionId,
        long userId,
        String clientRequestId,
        String requestHashVersion,
        String requestHash,
        String planId,
        Integer planVersion,
        String status,
        String traceId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        LocalDateTime expireAt) {
}
