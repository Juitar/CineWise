package com.miaoyu.ticket.agent.domain.run;

import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import java.util.Objects;

/** 单个运行节点的不可变执行状态，不保存工具业务数据。 */
public final class ExecutionNodeState {
    private static final int MAX_RETRY_COUNT = 1;

    private final String nodeId;
    private final PlanNodeStatus status;
    private final int attemptCount;
    private final int retryCount;
    private final ToolStatus lastToolStatus;
    private final Integer lastErrorCode;
    private final boolean autoSkipped;
    private final String skipReason;
    private final String skipSourceNodeId;

    private ExecutionNodeState(
            String nodeId,
            PlanNodeStatus status,
            int attemptCount,
            int retryCount,
            ToolStatus lastToolStatus,
            Integer lastErrorCode,
            boolean autoSkipped,
            String skipReason,
            String skipSourceNodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId 不能为空");
        }
        this.nodeId = nodeId;
        this.status = Objects.requireNonNull(status, "节点状态不能为空");
        validateCounters(status, attemptCount, retryCount);
        this.attemptCount = attemptCount;
        this.retryCount = retryCount;
        this.lastToolStatus = lastToolStatus;
        this.lastErrorCode = lastErrorCode;
        this.autoSkipped = autoSkipped;
        this.skipReason = skipReason;
        this.skipSourceNodeId = skipSourceNodeId;
    }

    /** 返回节点标识。 */
    public String nodeId() {
        return nodeId;
    }

    /** 返回当前节点状态。 */
    public PlanNodeStatus status() {
        return status;
    }

    /** 返回节点已开始的次数。 */
    public int attemptCount() {
        return attemptCount;
    }

    /** 返回已消耗的自动重试次数。 */
    public int retryCount() {
        return retryCount;
    }

    /** 返回最近一次工具反馈状态。 */
    public ToolStatus lastToolStatus() {
        return lastToolStatus;
    }

    /** 返回最近一次工具错误码。 */
    public Integer lastErrorCode() {
        return lastErrorCode;
    }

    /** 返回节点是否由状态机自动跳过。 */
    public boolean autoSkipped() {
        return autoSkipped;
    }

    /** 返回自动跳过原因。 */
    public String skipReason() {
        return skipReason;
    }

    /** 返回造成自动跳过的最初节点。 */
    public String skipSourceNodeId() {
        return skipSourceNodeId;
    }

    static ExecutionNodeState initial(ExecutionPlanNode node) {
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

    ExecutionNodeState start() {
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

    ExecutionNodeState succeed() {
        requireStatus(PlanNodeStatus.RUNNING);
        return new ExecutionNodeState(
                nodeId,
                PlanNodeStatus.SUCCESS,
                attemptCount,
                retryCount,
                lastToolStatus,
                lastErrorCode,
                autoSkipped,
                skipReason,
                skipSourceNodeId);
    }

    ExecutionNodeState succeed(ToolResult<?> result) {
        requireStatus(PlanNodeStatus.RUNNING);
        return withToolResult(PlanNodeStatus.SUCCESS, result, retryCount);
    }

    ExecutionNodeState processing(ToolResult<?> result) {
        requireStatus(PlanNodeStatus.RUNNING);
        return withToolResult(PlanNodeStatus.RUNNING, result, retryCount);
    }

    ExecutionNodeState retry(ToolResult<?> result) {
        requireStatus(PlanNodeStatus.RUNNING);
        return withToolResult(PlanNodeStatus.PENDING, result, retryCount + 1);
    }

    ExecutionNodeState fail(ToolResult<?> result) {
        requireStatus(PlanNodeStatus.RUNNING);
        return withToolResult(PlanNodeStatus.FAILED, result, retryCount);
    }

    ExecutionNodeState fail() {
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

    ExecutionNodeState skipForUpstreamFailure(String sourceNodeId) {
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

    private static void validateCounters(PlanNodeStatus status, int attemptCount, int retryCount) {
        if (attemptCount < 0 || retryCount < 0 || retryCount > attemptCount || retryCount > MAX_RETRY_COUNT) {
            throw new IllegalArgumentException("节点尝试次数或重试次数不合法");
        }
        if ((status == PlanNodeStatus.RUNNING || status == PlanNodeStatus.SUCCESS || status == PlanNodeStatus.FAILED)
                && attemptCount == 0) {
            throw new IllegalArgumentException("已开始或终态节点必须至少有一次执行尝试");
        }
    }

    private void requireStatus(PlanNodeStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException("节点 " + nodeId + " 当前状态为 " + status + "，不能执行该操作");
        }
    }
}
