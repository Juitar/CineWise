package com.miaoyu.ticket.agent.domain.persistence;

import java.time.LocalDateTime;
import java.util.Objects;

/** B 自有的 Agent 会话事实；activeRunId 使用内部运行 ID，不使用对外 runId。 */
public record AgentSession(
        long id,
        String sessionId,
        long userId,
        String summary,
        AgentSessionStatus status,
        Long activeRunId,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        LocalDateTime expireAt) {

    public AgentSession {
        requirePositive(id, "会话内部 ID");
        requireText(sessionId, "sessionId");
        requirePositive(userId, "用户 ID");
        Objects.requireNonNull(status, "会话状态不能为空");
        if (activeRunId != null) {
            requirePositive(activeRunId, "活动运行内部 ID");
        }
        if (status == AgentSessionStatus.CLEARED && activeRunId != null) {
            throw new IllegalArgumentException("已清空会话不能保留活动运行");
        }
        requireNonNegative(version, "会话版本");
        createTime = Objects.requireNonNull(createTime, "会话创建时间不能为空");
        updateTime = Objects.requireNonNull(updateTime, "会话更新时间不能为空");
        expireAt = Objects.requireNonNull(expireAt, "会话到期时间不能为空");
        if (updateTime.isBefore(createTime) || expireAt.isBefore(createTime)) {
            throw new IllegalArgumentException("会话时间范围不合法");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + "不能为负数");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
    }
}
