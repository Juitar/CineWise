package com.miaoyu.ticket.agent.domain.run;

import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import java.util.Objects;

/** 单个运行节点的不可变执行状态，不保存工具业务数据。 */
public record ExecutionNodeState(
        String nodeId,
        PlanNodeStatus status,
        int attemptCount,
        int retryCount,
        ToolStatus lastToolStatus,
        Integer lastErrorCode,
        boolean autoSkipped,
        String skipReason,
        String skipSourceNodeId) {

    public ExecutionNodeState {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId 不能为空");
        }
        Objects.requireNonNull(status, "节点状态不能为空");
        if (attemptCount < 0 || retryCount < 0) {
            throw new IllegalArgumentException("尝试次数不能为负数");
        }
    }

    /** 根据已校验计划节点创建初始状态。 */
    public static ExecutionNodeState initial(ExecutionPlanNode node) {
        Objects.requireNonNull(node, "计划节点不能为空");
        return new ExecutionNodeState(
                node.nodeId(),
                node.status(),
                0,
                0,
                null,
                null,
                node.autoSkipped(),
                node.skipReason(),
                node.skipSourceNodeId());
    }

    /** 将等待节点标记为本次开始执行。 */
    public ExecutionNodeState start() {
        requireStatus(PlanNodeStatus.PENDING);
        return new ExecutionNodeState(
                nodeId,
                PlanNodeStatus.RUNNING,
                attemptCount + 1,
                retryCount,
                lastToolStatus,
                lastErrorCode,
                autoSkipped,
                skipReason,
                skipSourceNodeId);
    }

    /** 将执行中的节点标记为成功。 */
    public ExecutionNodeState succeed(ToolResult<?> result) {
        requireStatus(PlanNodeStatus.RUNNING);
        return withToolResult(PlanNodeStatus.SUCCESS, result, retryCount);
    }

    /** 保持节点执行中，并记录工具仍在处理的反馈。 */
    public ExecutionNodeState processing(ToolResult<?> result) {
        requireStatus(PlanNodeStatus.RUNNING);
        return withToolResult(PlanNodeStatus.RUNNING, result, retryCount);
    }

    /** 将首次可重试失败的节点重新放回等待状态。 */
    public ExecutionNodeState retry(ToolResult<?> result) {
        requireStatus(PlanNodeStatus.RUNNING);
        return withToolResult(PlanNodeStatus.PENDING, result, retryCount + 1);
    }

    /** 将执行中的节点标记为最终失败。 */
    public ExecutionNodeState fail(ToolResult<?> result) {
        requireStatus(PlanNodeStatus.RUNNING);
        return withToolResult(PlanNodeStatus.FAILED, result, retryCount);
    }

    /** 将执行中的非工具节点标记为最终失败。 */
    public ExecutionNodeState fail() {
        requireStatus(PlanNodeStatus.RUNNING);
        return new ExecutionNodeState(
                nodeId,
                PlanNodeStatus.FAILED,
                attemptCount,
                retryCount,
                lastToolStatus,
                lastErrorCode,
                autoSkipped,
                skipReason,
                skipSourceNodeId);
    }

    /** 因前置分支无法继续而跳过尚未开始的节点。 */
    public ExecutionNodeState skipForUpstreamFailure(String sourceNodeId) {
        requireStatus(PlanNodeStatus.PENDING);
        if (sourceNodeId == null || sourceNodeId.isBlank()) {
            throw new IllegalArgumentException("跳过来源节点不能为空");
        }
        return new ExecutionNodeState(
                nodeId,
                PlanNodeStatus.SKIPPED,
                attemptCount,
                retryCount,
                lastToolStatus,
                lastErrorCode,
                true,
                "UPSTREAM_FAILED",
                sourceNodeId);
    }

    private ExecutionNodeState withToolResult(PlanNodeStatus nextStatus, ToolResult<?> result, int nextRetryCount) {
        Objects.requireNonNull(result, "工具结果不能为空");
        return new ExecutionNodeState(
                nodeId,
                nextStatus,
                attemptCount,
                nextRetryCount,
                result.status(),
                result.errorCode(),
                autoSkipped,
                skipReason,
                skipSourceNodeId);
    }

    private void requireStatus(PlanNodeStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException("节点 " + nodeId + " 当前状态为 " + status + "，不能执行该操作");
        }
    }
}
