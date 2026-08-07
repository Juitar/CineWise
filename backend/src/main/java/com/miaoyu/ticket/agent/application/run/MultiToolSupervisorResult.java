package com.miaoyu.ticket.agent.application.run;

import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
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
        String safeNextAction,
        ReplyGenerationResponse generatedReply) {

    public MultiToolSupervisorResult(
            CandidatePlan candidatePlan,
            PlanValidationResult validation,
            ExecutionRunState state,
            List<NodeToolResult> toolResults,
            boolean awaitingConfirmation,
            String safeNextAction) {
        this(candidatePlan, validation, state, toolResults, awaitingConfirmation, safeNextAction, null);
    }

    public MultiToolSupervisorResult {
        toolResults = List.copyOf(toolResults == null ? List.of() : toolResults);
    }

    /** 单个节点的安全结果关联，供持久化层映射状态而不依赖结果列表顺序。 */
    public record NodeToolResult(String nodeId, String targetName, ToolResult<?> result) {
        public NodeToolResult(String nodeId, ToolResult<?> result) {
            this(nodeId, null, result);
        }

        public NodeToolResult {
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalArgumentException("nodeId 不能为空");
            }
            if (result == null) {
                throw new IllegalArgumentException("工具结果不能为空");
            }
            if (targetName != null && targetName.isBlank()) {
                throw new IllegalArgumentException("targetName 不能为空白");
            }
        }
    }
}
