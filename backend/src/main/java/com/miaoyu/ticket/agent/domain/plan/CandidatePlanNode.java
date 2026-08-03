package com.miaoyu.ticket.agent.domain.plan;

import java.util.List;

/** 模型可提出、尚未获得服务端执行状态的候选节点。 */
public record CandidatePlanNode(
        String nodeId,
        PlanNodeType type,
        String targetName,
        List<InputReference> inputRefs,
        List<String> dependsOn,
        FailurePolicy failurePolicy) {

    public CandidatePlanNode {
        inputRefs = inputRefs == null ? List.of() : List.copyOf(inputRefs);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
