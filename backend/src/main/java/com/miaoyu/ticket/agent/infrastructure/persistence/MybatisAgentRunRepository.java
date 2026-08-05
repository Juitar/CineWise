package com.miaoyu.ticket.agent.infrastructure.persistence;

import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 运行查询和终态写入实现；恢复查询只返回超过阈值的 RUNNING 记录。 */
@Repository
public class MybatisAgentRunRepository implements AgentRunRepository {
    private final AgentPersistenceMapper mapper;

    public MybatisAgentRunRepository(AgentPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AgentRun> findByRunIdAndUserId(String runId, long userId) {
        return Optional.ofNullable(mapper.findRunByRunIdAndUserId(runId, userId))
                .map(AgentPersistenceMappings::toDomain);
    }

    @Override
    public Optional<AgentRun> findByClientRequestId(long userId, long sessionId, String clientRequestId) {
        return Optional.ofNullable(mapper.findRunByClientRequestId(userId, sessionId, clientRequestId))
                .map(AgentPersistenceMappings::toDomain);
    }

    @Override
    public List<AgentRun> findStaleRunningBefore(LocalDateTime cutoff, int limit) {
        return mapper.findStaleRunningBefore(cutoff, limit).stream().map(AgentPersistenceMappings::toDomain).toList();
    }

    @Override
    public void insert(AgentRun run) {
        mapper.insertRun(AgentPersistenceMappings.toEntity(run));
    }

    @Override
    public boolean updateRunningPlanWithCas(AgentRun run, long expectedVersion) {
        return mapper.updateRunRunningPlanWithCas(AgentPersistenceMappings.toEntity(run), expectedVersion) == 1;
    }

    @Override
    public boolean updateTerminalWithCas(AgentRun run, long expectedVersion) {
        return mapper.updateRunTerminalWithCas(AgentPersistenceMappings.toEntity(run), expectedVersion) == 1;
    }

    @Override
    public boolean deleteTerminalExpiredById(long runId, LocalDateTime now) {
        return mapper.deleteTerminalExpiredRunById(runId, now) == 1;
    }

    @Override
    public boolean hasRuns(long sessionId) {
        return mapper.countRunsBySessionId(sessionId) > 0;
    }
}
