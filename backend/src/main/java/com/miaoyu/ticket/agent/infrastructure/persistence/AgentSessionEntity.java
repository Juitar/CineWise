package com.miaoyu.ticket.agent.infrastructure.persistence;

import java.time.LocalDateTime;

/** `agent_session` 的持久化行，只在 B 的基础设施层使用。 */
public record AgentSessionEntity(
        long id,
        String sessionId,
        long userId,
        String summary,
        String status,
        Long activeRunId,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        LocalDateTime expireAt) {
}
