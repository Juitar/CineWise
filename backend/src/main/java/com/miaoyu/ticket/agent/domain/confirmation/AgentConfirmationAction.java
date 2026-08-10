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
        // 创建时验证全部事实，后续状态转换只通过不可变副本更新，避免出现半更新动作。
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
            // 参数哈希必须由最终确认的命令计算，不能接受客户端传入的任意哈希。
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
        // 写请求一旦开始就必须持久化稳定标识，重试只查询原结果而不能重新发起写入。
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
        // 初始动作尚未产生外部写请求，因此没有写标识、结果引用和恢复信息。
        return new AgentConfirmationAction(
                id, actionId, userId, agentSessionId, agentRunId, runId, planId, planVersion, nodeId, command,
                AgentActionParameterHash.from(command), expireAt, AgentConfirmationActionStatus.PENDING_CONFIRMATION,
                null, null, null, null, null, 0L, now, now);
    }

    public boolean isExpiredAt(LocalDateTime now) {
        // 到期时刻本身已不可确认，使用非严格比较避免边界重复提交。
        return !Objects.requireNonNull(now, "now 不能为空").isBefore(expireAt);
    }

    public AgentConfirmationAction claim(AgentActionWriteIdentifiers identifiers, LocalDateTime now) {
        // 仅允许从待确认进入执行中，防止用户重复点击重新领取同一动作。
        requireStatus(AgentConfirmationActionStatus.PENDING_CONFIRMATION);
        return with(
                AgentConfirmationActionStatus.EXECUTING, Objects.requireNonNull(identifiers), null, null, null, null,
                now);
    }

    public AgentConfirmationAction reject(LocalDateTime now) {
        // 拒绝不产生写标识，后续不能再对该动作执行确认。
        requireStatus(AgentConfirmationActionStatus.PENDING_CONFIRMATION);
        return with(AgentConfirmationActionStatus.REJECTED, null, null, null, null, null, now);
    }

    public AgentConfirmationAction expire(LocalDateTime now) {
        requireStatus(AgentConfirmationActionStatus.PENDING_CONFIRMATION);
        return with(AgentConfirmationActionStatus.EXPIRED, null, null, null, null, null, now);
    }

    public AgentConfirmationAction invalidate(String hint, LocalDateTime now) {
        // 上游计划过期等业务原因会使动作失效，并保留提示供卡片展示。
        requireStatus(AgentConfirmationActionStatus.PENDING_CONFIRMATION);
        requireText(hint, "失效提示");
        return with(AgentConfirmationActionStatus.INVALIDATED, null, null, null, null, null, now);
    }

    public AgentConfirmationAction markSucceeded(String reference, LocalDateTime now) {
        // 成功结果只保存可查询的引用，不能把订单完整数据复制到 Agent 动作表。
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
        // 写请求超时并不等于失败，保留 30 天恢复窗口，禁止自动重放写请求。
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
        // 更新时间不得倒退，版本递增交由构造器的不可变快照统一表达。
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
        // 只有已执行过的动作才允许落结果，待确认、拒绝和过期动作没有可写结果。
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
