package com.miaoyu.ticket.agent.domain.plan;

import java.util.List;

/** 服务端校验通过后可交给后续执行器的运行节点。 */
public record ExecutionPlanNode(
        String nodeId,
        PlanNodeType type,
        String targetName,
        List<InputReference> inputRefs,
        List<String> dependsOn,
        FailurePolicy failurePolicy,
        String businessParameterHash,
        PlanNodeStatus status,
        boolean requiresConfirmation,
        boolean autoSkipped,
        String skipReason,
        String skipSourceNodeId,
        SlotSnapshot slotSnapshot) {

    public ExecutionPlanNode {
        inputRefs = List.copyOf(inputRefs);
        dependsOn = List.copyOf(dependsOn);
    }
}
