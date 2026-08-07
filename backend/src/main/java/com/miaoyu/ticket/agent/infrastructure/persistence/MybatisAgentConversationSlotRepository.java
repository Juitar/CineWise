package com.miaoyu.ticket.agent.infrastructure.persistence;

import com.miaoyu.ticket.agent.application.persistence.AgentConversationSlotRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis 边界只暴露会话槽位 JSON，领域层不依赖持久化行对象。 */
@Repository
public class MybatisAgentConversationSlotRepository implements AgentConversationSlotRepository {
    private final AgentPersistenceMapper mapper;

    public MybatisAgentConversationSlotRepository(AgentPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<String> findBySessionIdAndUserId(String sessionId, long userId) {
        return Optional.ofNullable(mapper.findSessionSlotSnapshot(sessionId, userId));
    }

    @Override
    public boolean update(long sessionId, long userId, long expectedVersion, String slotSnapshotJson) {
        return mapper.updateSessionSlotSnapshot(sessionId, userId, expectedVersion, slotSnapshotJson) == 1;
    }
}
