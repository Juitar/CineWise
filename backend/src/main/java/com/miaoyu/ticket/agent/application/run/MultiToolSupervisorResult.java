package com.miaoyu.ticket.agent.application.run;

import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationResult;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import java.util.List;

/** Supervisor 的安全运行结果；只返回节点标识与公共 ToolResult，不保留模型原文或完整外部响应。 */
public record MultiToolSupervisorResult(
        CandidatePlan candidatePlan,
        PlanValidationResult validation,
        ExecutionRunState state,
        List<NodeToolResult> toolResults,
        boolean awaitingConfirmation,
        String safeNextAction) {

    public MultiToolSupervisorResult {
        toolResults = List.copyOf(toolResults == null ? List.of() : toolResults);
    }

    /** 单个节点的安全结果关联，供持久化层映射状态而不依赖结果列表顺序。 */
    public record NodeToolResult(String nodeId, ToolResult<?> result) {
        public NodeToolResult {
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalArgumentException("nodeId 不能为空");
            }
            if (result == null) {
                throw new IllegalArgumentException("工具结果不能为空");
            }
        }
    }
}
