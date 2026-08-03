package com.miaoyu.ticket.agent.domain.plan;

import java.util.List;

/** 已校验的运行计划，本 change 不负责调度或推进其状态。 */
public record ExecutionPlan(String planId, int version, List<ExecutionPlanNode> nodes) {

    public ExecutionPlan {
        nodes = List.copyOf(nodes);
    }
}
