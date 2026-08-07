package com.miaoyu.ticket.agent.application.persistence;

import java.util.Optional;

/** 会话槽位只属于 B 的 agent_session，查询和更新始终绑定当前用户。 */
public interface AgentConversationSlotRepository {
    Optional<String> findBySessionIdAndUserId(String sessionId, long userId);
    boolean update(long sessionId, long userId, long expectedVersion, String slotSnapshotJson);
}
