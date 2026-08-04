package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import java.util.List;

/** B 自有运行步骤存储端口；任何推进必须使用 version 和原状态共同比较。 */
public interface AgentRunStepRepository {

    void insertAll(List<AgentRunStep> steps);

    List<AgentRunStep> findByRunId(long runId);

    boolean updateWithCas(AgentRunStep nextStep, long expectedVersion, PlanNodeStatus expectedStatus);
}
