package com.miaoyu.ticket.agent.infrastructure.persistence;

import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 仅保存和读取当前用户会话中的可展示消息。 */
@Repository
public class MybatisAgentMessageRepository implements AgentMessageRepository {
    private final AgentPersistenceMapper mapper;

    public MybatisAgentMessageRepository(AgentPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(AgentMessage message) {
        mapper.insertMessage(AgentPersistenceMappings.toEntity(message));
    }

    @Override
    public List<AgentMessage> findBySessionIdAndUserId(long sessionId, long userId, int limit) {
        return mapper.findMessagesBySessionIdAndUserId(sessionId, userId, limit).stream()
                .map(AgentPersistenceMappings::toDomain)
                .toList();
    }

    @Override
    public List<AgentMessage> findByRunIdAndUserId(long runId, long userId) {
        return mapper.findMessagesByRunIdAndUserId(runId, userId).stream()
                .map(AgentPersistenceMappings::toDomain)
                .toList();
    }
}
