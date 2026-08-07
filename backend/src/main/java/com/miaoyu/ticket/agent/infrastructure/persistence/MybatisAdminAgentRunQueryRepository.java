package com.miaoyu.ticket.agent.infrastructure.persistence;

import com.miaoyu.ticket.agent.application.audit.AdminAgentRunQueryRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 管理员运行列表只从 B 的 Agent 表读取；不关联认证或业务模块表。 */
@Repository
public class MybatisAdminAgentRunQueryRepository implements AdminAgentRunQueryRepository {
    private final AgentPersistenceMapper mapper;

    public MybatisAdminAgentRunQueryRepository(AgentPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long count(Criteria criteria) {
        return mapper.countAdminRuns(criteria);
    }

    @Override
    public List<AgentRun> findPage(Criteria criteria) {
        return mapper.findAdminRunPage(criteria).stream().map(AgentPersistenceMappings::toDomain).toList();
    }

    @Override
    public Optional<AgentRun> findByRunId(String runId) {
        return Optional.ofNullable(mapper.findAdminRunByRunId(runId)).map(AgentPersistenceMappings::toDomain);
    }
}
