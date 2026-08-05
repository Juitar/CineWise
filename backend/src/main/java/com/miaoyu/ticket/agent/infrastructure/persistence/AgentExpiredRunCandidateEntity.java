package com.miaoyu.ticket.agent.infrastructure.persistence;

/** 到期清理查询行，仅在 MyBatis 基础设施层使用。 */
public record AgentExpiredRunCandidateEntity(long runId, String externalRunId, long userId, String sessionId) {
}
