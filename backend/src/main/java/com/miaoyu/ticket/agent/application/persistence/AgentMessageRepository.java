package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import java.util.List;
import java.util.Optional;

/** B 自有展示消息存储端口；消息不保存模型原始上下文。 */
public interface AgentMessageRepository {

    void insert(AgentMessage message);

    List<AgentMessage> findBySessionIdAndUserId(long sessionId, long userId, int limit);

    List<AgentMessage> findBySessionIdAndUserId(long sessionId, long userId, int offset, int limit);

    Optional<AgentMessage> findLatestPlanCardBySessionIdAndUserId(long sessionId, long userId);

    long countBySessionIdAndUserId(long sessionId, long userId);

    List<AgentMessage> findByRunIdAndUserId(long runId, long userId);

    int deleteByRunId(long runId);
}
