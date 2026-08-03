package com.miaoyu.ticket.agent.domain.plan;

import java.util.List;

/** 模型网关提出、必须先经服务端校验的计划。 */
public record CandidatePlan(String planId, int version, List<CandidatePlanNode> nodes) {

    public CandidatePlan {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
