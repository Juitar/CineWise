package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import java.util.List;

/** B 自有展示消息存储端口；消息不保存模型原始上下文。 */
public interface AgentMessageRepository {

    void insert(AgentMessage message);

    List<AgentMessage> findBySessionIdAndUserId(long sessionId, long userId, int limit);

    List<AgentMessage> findByRunIdAndUserId(long runId, long userId);

    int deleteByRunId(long runId);
}
