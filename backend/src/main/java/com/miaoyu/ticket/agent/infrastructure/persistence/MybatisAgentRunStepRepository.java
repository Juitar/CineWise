package com.miaoyu.ticket.agent.infrastructure.persistence;

import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 节点推进通过数据库 version 与前置状态共同比较，拒绝覆盖并发写入。 */
@Repository
public class MybatisAgentRunStepRepository implements AgentRunStepRepository {
    private final AgentPersistenceMapper mapper;

    public MybatisAgentRunStepRepository(AgentPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertAll(List<AgentRunStep> steps) {
        steps.forEach(step -> mapper.insertRunStep(AgentPersistenceMappings.toEntity(step)));
    }

    @Override
    public List<AgentRunStep> findByRunId(long runId) {
        return mapper.findStepsByRunId(runId).stream().map(AgentPersistenceMappings::toDomain).toList();
    }

    @Override
    public boolean updateWithCas(AgentRunStep nextStep, long expectedVersion, PlanNodeStatus expectedStatus) {
        return mapper.updateRunStepWithCas(
                        AgentPersistenceMappings.toEntity(nextStep), expectedVersion, expectedStatus.name())
                == 1;
    }
}
