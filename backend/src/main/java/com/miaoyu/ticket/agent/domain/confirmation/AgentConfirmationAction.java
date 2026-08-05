package com.miaoyu.ticket.agent.domain.confirmation;

import java.time.LocalDateTime;
import java.util.Objects;

/** 确认动作的不可变服务端事实；不保存金额、订单明细或模型原始输出。 */
public record AgentConfirmationAction(
        long id,
        String actionId,
        long userId,
        long agentSessionId,
        long agentRunId,
        String runId,
        String planId,
        int planVersion,
        String nodeId,
        ConfirmedOrderCommand command,
        AgentActionParameterHash parameterHash,
        LocalDateTime expireAt,
        AgentConfirmationActionStatus status,
        AgentActionWriteIdentifiers writeIdentifiers,
        String resultReference,
        String recoveryHint,
        LocalDateTime resultUnknownAt,
        LocalDateTime recoveryUntil,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public AgentConfirmationAction {
        requirePositive(id, "action 内部 ID");
        requireText(actionId, "actionId");
        if (actionId.length() > 36) {
            throw new IllegalArgumentException("actionId 最多 36 个字符");
        }
        requirePositive(userId, "userId");
        requirePositive(agentSessionId, "agentSessionId");
        requirePositive(agentRunId, "agentRunId");
        requireText(runId, "runId");
        requireText(planId, "planId");
        if (planVersion < 1) {
            throw new IllegalArgumentException("planVersion 必须大于零");
        }
        requireText(nodeId, "nodeId");
        Objects.requireNonNull(command, "command 不能为空");
        Objects.requireNonNull(parameterHash, "parameterHash 不能为空");
        if (!parameterHash.equals(AgentActionParameterHash.from(command))) {
            throw new IllegalArgumentException("parameterHash 必须由已校验 command 计算");
        }
        expireAt = Objects.requireNonNull(expireAt, "expireAt 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("version 不能为负数");
        }
        createTime = Objects.requireNonNull(createTime, "createTime 不能为空");
        updateTime = Objects.requireNonNull(updateTime, "updateTime 不能为空");
        if (updateTime.isBefore(createTime) || expireAt.isBefore(createTime)) {
            throw new IllegalArgumentException("action 时间范围不合法");
        }
        boolean hasWriteIdentifiers = writeIdentifiers != null;
        if ((status == AgentConfirmationActionStatus.EXECUTING
                        || status == AgentConfirmationActionStatus.RESULT_UNKNOWN
                        || status == AgentConfirmationActionStatus.SUCCEEDED
                        || status == AgentConfirmationActionStatus.FAILED)
                != hasWriteIdentifiers) {
            throw new IllegalArgumentException("写执行状态与稳定写标识不一致");
        }
        if (status == AgentConfirmationActionStatus.SUCCEEDED && isBlank(resultReference)) {
            throw new IllegalArgumentException("成功 action 必须保存结果引用");
        }
        if (status == AgentConfirmationActionStatus.RESULT_UNKNOWN
                && (isBlank(recoveryHint) || resultUnknownAt == null || recoveryUntil == null
                || !recoveryUntil.equals(resultUnknownAt.plusDays(30)))) {
            throw new IllegalArgumentException("结果未知 action 必须保存 30 天恢复信息");
        }
        if (status != AgentConfirmationActionStatus.RESULT_UNKNOWN
                && (recoveryHint != null || resultUnknownAt != null || recoveryUntil != null)) {
            throw new IllegalArgumentException("恢复信息只能用于结果未知 action");
        }
    }

    public static AgentConfirmationAction pending(
            long id,
            String actionId,
            long userId,
            long agentSessionId,
            long agentRunId,
            String runId,
            String planId,
            int planVersion,
            String nodeId,
            ConfirmedOrderCommand command,
            LocalDateTime expireAt,
            LocalDateTime now) {
        return new AgentConfirmationAction(
                id, actionId, userId, agentSessionId, agentRunId, runId, planId, planVersion, nodeId, command,
                AgentActionParameterHash.from(command), expireAt, AgentConfirmationActionStatus.PENDING_CONFIRMATION,
                null, null, null, null, null, 0L, now, now);
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !Objects.requireNonNull(now, "now 不能为空").isBefore(expireAt);
    }

    public AgentConfirmationAction claim(AgentActionWriteIdentifiers identifiers, LocalDateTime now) {
        requireStatus(AgentConfirmationActionStatus.PENDING_CONFIRMATION);
        return with(
                AgentConfirmationActionStatus.EXECUTING, Objects.requireNonNull(identifiers), null, null, null, null,
                now);
    }

    public AgentConfirmationAction reject(LocalDateTime now) {
        requireStatus(AgentConfirmationActionStatus.PENDING_CONFIRMATION);
        return with(AgentConfirmationActionStatus.REJECTED, null, null, null, null, null, now);
    }

    public AgentConfirmationAction expire(LocalDateTime now) {
        requireStatus(AgentConfirmationActionStatus.PENDING_CONFIRMATION);
        return with(AgentConfirmationActionStatus.EXPIRED, null, null, null, null, null, now);
    }

    public AgentConfirmationAction invalidate(String hint, LocalDateTime now) {
        requireStatus(AgentConfirmationActionStatus.PENDING_CONFIRMATION);
        requireText(hint, "失效提示");
        return with(AgentConfirmationActionStatus.INVALIDATED, null, null, null, null, null, now);
    }

    public AgentConfirmationAction markSucceeded(String reference, LocalDateTime now) {
        requireResultKnownTransition();
        return with(
                AgentConfirmationActionStatus.SUCCEEDED, writeIdentifiers, requireText(reference, "结果引用"), null,
                null, null, now);
    }

    public AgentConfirmationAction markFailed(String hint, LocalDateTime now) {
        requireResultKnownTransition();
        requireText(hint, "失败提示");
        return with(AgentConfirmationActionStatus.FAILED, writeIdentifiers, null, null, null, null, now);
    }

    public AgentConfirmationAction markResultUnknown(String hint, LocalDateTime now) {
        requireStatus(AgentConfirmationActionStatus.EXECUTING);
        return with(
                AgentConfirmationActionStatus.RESULT_UNKNOWN, writeIdentifiers, null, requireText(hint, "恢复提示"), now,
                now.plusDays(30), now);
    }

    private AgentConfirmationAction with(
            AgentConfirmationActionStatus nextStatus,
            AgentActionWriteIdentifiers nextWriteIdentifiers,
            String nextResultReference,
            String nextRecoveryHint,
            LocalDateTime nextResultUnknownAt,
            LocalDateTime nextRecoveryUntil,
            LocalDateTime now) {
        LocalDateTime update = Objects.requireNonNull(now, "now 不能为空");
        if (update.isBefore(updateTime)) {
            throw new IllegalArgumentException("action 更新时间不能倒退");
        }
        return new AgentConfirmationAction(
                id, actionId, userId, agentSessionId, agentRunId, runId, planId, planVersion, nodeId, command,
                parameterHash,
                expireAt, nextStatus, nextWriteIdentifiers, nextResultReference, nextRecoveryHint, nextResultUnknownAt,
                nextRecoveryUntil,
                version + 1, createTime, update);
    }

    private void requireResultKnownTransition() {
        if (status != AgentConfirmationActionStatus.EXECUTING
                && status != AgentConfirmationActionStatus.RESULT_UNKNOWN) {
            throw new IllegalStateException("当前 action 不能保存写结果");
        }
    }

    private void requireStatus(AgentConfirmationActionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("当前 action 状态不允许该操作");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String requireText(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static void requirePositive(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + "必须为正数");
        }
    }
}
