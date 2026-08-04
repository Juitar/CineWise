package com.miaoyu.ticket.agent.domain.persistence;

import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import java.time.LocalDateTime;
import java.util.Objects;

/** 已校验计划节点的持久化快照；推进时必须同时使用 version 和原状态比较更新。 */
public record AgentRunStep(
        long id,
        long runId,
        int planVersion,
        String nodeId,
        PlanNodeType nodeType,
        AgentStoredJson dependsOn,
        AgentStoredJson inputRefs,
        PlanNodeStatus status,
        FailurePolicy failurePolicy,
        int attemptCount,
        int retryCount,
        boolean recoveryPending,
        boolean autoSkipped,
        String skipReason,
        String skipSourceNodeId,
        AgentStoredJson slotSnapshot,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        LocalDateTime expireAt) {
    private static final String UPSTREAM_FAILED = "UPSTREAM_FAILED";

    public AgentRunStep {
        requirePositive(id, "步骤内部 ID");
        requirePositive(runId, "运行内部 ID");
        if (planVersion < 1) {
            throw new IllegalArgumentException("计划版本必须大于零");
        }
        requireText(nodeId, "节点 ID");
        Objects.requireNonNull(nodeType, "节点类型不能为空");
        if (nodeType == PlanNodeType.CONFIRM_ACTION) {
            throw new IllegalArgumentException("V008 不保存确认节点");
        }
        Objects.requireNonNull(dependsOn, "节点依赖快照不能为空");
        Objects.requireNonNull(inputRefs, "节点输入引用快照不能为空");
        Objects.requireNonNull(status, "节点状态不能为空");
        if (status == PlanNodeStatus.WAITING_CONFIRMATION) {
            throw new IllegalArgumentException("V008 不保存等待确认状态");
        }
        Objects.requireNonNull(failurePolicy, "失败策略不能为空");
        validateCounters(attemptCount, retryCount);
        validateRecovery(status, recoveryPending);
        validateStateTime(status, attemptCount, startedAt, finishedAt);
        validateSkip(status, autoSkipped, skipReason, skipSourceNodeId);
        Objects.requireNonNull(slotSnapshot, "槽位快照不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("步骤版本不能为负数");
        }
        createTime = Objects.requireNonNull(createTime, "步骤创建时间不能为空");
        updateTime = Objects.requireNonNull(updateTime, "步骤更新时间不能为空");
        expireAt = Objects.requireNonNull(expireAt, "步骤到期时间不能为空");
        if (updateTime.isBefore(createTime) || expireAt.isBefore(createTime)) {
            throw new IllegalArgumentException("步骤时间范围不合法");
        }
    }

    private static void validateCounters(int attemptCount, int retryCount) {
        if (attemptCount < 0 || retryCount < 0 || retryCount > attemptCount || retryCount > 1) {
            throw new IllegalArgumentException("步骤尝试次数或重试次数不合法");
        }
    }

    private static void validateRecovery(PlanNodeStatus status, boolean recoveryPending) {
        if (recoveryPending && status != PlanNodeStatus.RUNNING) {
            throw new IllegalArgumentException("恢复标记只能用于运行中节点");
        }
    }

    private static void validateStateTime(
            PlanNodeStatus status, int attemptCount, LocalDateTime startedAt, LocalDateTime finishedAt) {
        boolean isPending = status == PlanNodeStatus.PENDING
                && startedAt == null
                && finishedAt == null
                && attemptCount == 0;
        boolean isRunning = status == PlanNodeStatus.RUNNING
                && startedAt != null
                && finishedAt == null
                && attemptCount >= 1;
        boolean isFinished = (status == PlanNodeStatus.SUCCESS || status == PlanNodeStatus.FAILED)
                && startedAt != null
                && finishedAt != null
                && !finishedAt.isBefore(startedAt)
                && attemptCount >= 1;
        boolean isSkipped = status == PlanNodeStatus.SKIPPED
                && startedAt == null
                && finishedAt != null
                && attemptCount == 0;
        if (!(isPending || isRunning || isFinished || isSkipped)) {
            throw new IllegalArgumentException("步骤状态、时间和尝试次数不一致");
        }
    }

    private static void validateSkip(
            PlanNodeStatus status, boolean autoSkipped, String skipReason, String skipSourceNodeId) {
        if (autoSkipped) {
            if (status != PlanNodeStatus.SKIPPED
                    || !UPSTREAM_FAILED.equals(skipReason)
                    || skipSourceNodeId == null
                    || skipSourceNodeId.isBlank()) {
                throw new IllegalArgumentException("自动跳过信息不合法");
            }
            return;
        }
        if (skipReason != null || skipSourceNodeId != null) {
            throw new IllegalArgumentException("非自动跳过步骤不能保留跳过信息");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
    }
}
