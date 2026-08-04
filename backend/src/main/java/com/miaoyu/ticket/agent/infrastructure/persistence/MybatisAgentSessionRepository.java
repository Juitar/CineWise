package com.miaoyu.ticket.agent.infrastructure.persistence;

import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 通过条件更新维护会话活动运行位，数据库影响行数是最终并发判断。 */
@Repository
public class MybatisAgentSessionRepository implements AgentSessionRepository {
    private final AgentPersistenceMapper mapper;

    public MybatisAgentSessionRepository(AgentPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AgentSession> findBySessionIdAndUserId(String sessionId, long userId) {
        return Optional.ofNullable(mapper.findSessionBySessionIdAndUserId(sessionId, userId))
                .map(AgentPersistenceMappings::toDomain);
    }

    @Override
    public void insert(AgentSession session) {
        mapper.insertSession(AgentPersistenceMappings.toEntity(session));
    }

    @Override
    public boolean claimActiveRun(long sessionId, long userId, long runId, LocalDateTime runExpireAt) {
        return mapper.claimActiveRun(sessionId, userId, runId, runExpireAt) == 1;
    }

    @Override
    public boolean releaseActiveRun(long sessionId, long runId) {
        return mapper.releaseActiveRun(sessionId, runId) == 1;
    }
}
