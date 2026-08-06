package com.miaoyu.ticket.agent.domain.run;

import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import java.util.Objects;

/**
 * 单个运行节点的不可变执行状态，不保存工具业务数据。
 *
 * <p>状态只保留工具状态、稳定错误码和跳过原因，不能保存完整工具响应或异常对象。业务数据由应用层
 * 在本轮结果中持有，后续持久化能力也应采用受控审计字段，而不是把下游 DTO 原样写入节点状态。
 */
public final class ExecutionNodeState {
    /** 当前基础状态机只允许一次自动重试，写工具不属于该机制。 */
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
            // 节点标识是计划状态关联键，不能让空字符串进入快照后与其他节点混淆。
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

    /** 返回节点标识；它必须与执行计划中的 nodeId 一致。 */
    public String nodeId() {
        return nodeId;
    }

    /** 返回当前节点状态；状态变化只能由同包状态机入口创建。 */
    public PlanNodeStatus status() {
        return status;
    }

    /** 返回节点已开始的次数；重试会增加 attemptCount。 */
    public int attemptCount() {
        return attemptCount;
    }

    /** 返回已消耗的自动重试次数；该值不能大于 attemptCount。 */
    public int retryCount() {
        return retryCount;
    }

    /** 返回最近一次工具反馈状态；非工具节点可能保持 null。 */
    public ToolStatus lastToolStatus() {
        return lastToolStatus;
    }

    /** 返回最近一次工具错误码；只保存稳定码，不保存异常原文。 */
    public Integer lastErrorCode() {
        return lastErrorCode;
    }

    /** 返回节点是否由状态机自动跳过；人工取消不应复用该标记。 */
    public boolean autoSkipped() {
        return autoSkipped;
    }

    /** 返回自动跳过原因；当前仅用于上游失败传播。 */
    public String skipReason() {
        return skipReason;
    }

    /** 返回造成自动跳过的最初节点，便于多层下游追溯同一根因。 */
    public String skipSourceNodeId() {
        return skipSourceNodeId;
    }

    static ExecutionNodeState initial(ExecutionPlanNode node) {
        // 确认节点只等待用户动作，不会被只读调度器自动开始。
        Objects.requireNonNull(node, "计划节点不能为空");
        return new ExecutionNodeState(
                node.nodeId(),
                node.type() == com.miaoyu.ticket.agent.domain.plan.PlanNodeType.CONFIRM_ACTION
                        ? PlanNodeStatus.WAITING_CONFIRMATION : node.status(),
                0,
                0,
                null,
                null,
                node.autoSkipped(),
                node.skipReason(),
                node.skipSourceNodeId());
    }

    ExecutionNodeState start() {
        // 只有 PENDING 可开始，防止重复调用把同一个工具节点并发执行两次。
        if (status != PlanNodeStatus.PENDING && status != PlanNodeStatus.WAITING_CONFIRMATION) {
            throw new IllegalStateException("节点 " + nodeId + " 当前状态为 " + status + "，不能执行该操作");
        }
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
        // 非工具节点成功不产生 ToolResult，保留已有工具元数据而不凭空清空。
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
        // 工具成功必须记录本次 ToolStatus 和错误码字段，供上层区分真实结果与手动状态推进。
        requireStatus(PlanNodeStatus.RUNNING);
        return withToolResult(PlanNodeStatus.SUCCESS, result, retryCount);
    }

    ExecutionNodeState processing(ToolResult<?> result) {
        // PROCESSING 保持 RUNNING，避免把结果未知映射成失败后触发不安全重试。
        requireStatus(PlanNodeStatus.RUNNING);
        return withToolResult(PlanNodeStatus.RUNNING, result, retryCount);
    }

    ExecutionNodeState retry(ToolResult<?> result) {
        // 重试把节点回到 PENDING，下一次仍必须经过状态机选择条件检查。
        requireStatus(PlanNodeStatus.RUNNING);
        return withToolResult(PlanNodeStatus.PENDING, result, retryCount + 1);
    }

    ExecutionNodeState fail(ToolResult<?> result) {
        // 工具最终失败后状态不可逆，后续由状态机传播跳过到尚未开始的下游。
        requireStatus(PlanNodeStatus.RUNNING);
        return withToolResult(PlanNodeStatus.FAILED, result, retryCount);
    }

    ExecutionNodeState fail() {
        // 非工具节点失败没有工具元数据，仍保留此前字段以保证快照不可变语义。
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
        // 确认节点虽处于等待确认，但尚未开始工具调用；上游失败时必须与 PENDING 一样安全跳过。
        if (status != PlanNodeStatus.PENDING && status != PlanNodeStatus.WAITING_CONFIRMATION) {
            throw new IllegalStateException("节点 " + nodeId + " 当前状态为 " + status + "，不能执行该操作");
        }
        if (sourceNodeId == null || sourceNodeId.isBlank()) {
            // 跳过根因是审计和错误展示依据，不能留下不可追溯的 SKIPPED 状态。
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
        // 统一复制工具元数据，防止不同结果分支遗漏 retryable 之外的稳定错误信息。
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
            // 计数不变量阻止构造出“未执行却已重试”或无限重试的非法快照。
            throw new IllegalArgumentException("节点尝试次数或重试次数不合法");
        }
        if ((status == PlanNodeStatus.RUNNING || status == PlanNodeStatus.SUCCESS || status == PlanNodeStatus.FAILED)
                && attemptCount == 0) {
            // 已开始或终态节点必须至少尝试过一次，否则下游无法判断它是否真的执行过。
            throw new IllegalArgumentException("已开始或终态节点必须至少有一次执行尝试");
        }
    }

    private void requireStatus(PlanNodeStatus expectedStatus) {
        if (status != expectedStatus) {
            // 拒绝非法迁移而不是静默覆盖，可让调用方及时发现重复推进或错误调用顺序。
            throw new IllegalStateException("节点 " + nodeId + " 当前状态为 " + status + "，不能执行该操作");
        }
    }
}
