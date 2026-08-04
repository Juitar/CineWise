package com.miaoyu.ticket.agent.domain.persistence;

import java.time.LocalDateTime;
import java.util.Objects;

/** 单次用户 Agent 请求的持久化事实，不包含模型原始上下文或完整工具结果。 */
public record AgentRun(
        long id,
        String runId,
        long sessionId,
        long userId,
        String clientRequestId,
        AgentRequestHash requestHash,
        String planId,
        Integer planVersion,
        AgentRunStatus status,
        String traceId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        LocalDateTime expireAt) {

    public AgentRun {
        requirePositive(id, "运行内部 ID");
        requireText(runId, "runId");
        requirePositive(sessionId, "会话内部 ID");
        requirePositive(userId, "用户 ID");
        requireText(clientRequestId, "clientRequestId");
        Objects.requireNonNull(requestHash, "请求摘要不能为空");
        if (planVersion != null && planVersion < 1) {
            throw new IllegalArgumentException("计划版本必须大于零");
        }
        Objects.requireNonNull(status, "运行状态不能为空");
        requireText(traceId, "traceId");
        startedAt = Objects.requireNonNull(startedAt, "运行开始时间不能为空");
        if (status.isTerminal() != (finishedAt != null)) {
            throw new IllegalArgumentException("运行终态与完成时间不一致");
        }
        if (finishedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("运行完成时间不能早于开始时间");
        }
        requireNonNegative(version, "运行版本");
        createTime = Objects.requireNonNull(createTime, "运行创建时间不能为空");
        updateTime = Objects.requireNonNull(updateTime, "运行更新时间不能为空");
        expireAt = Objects.requireNonNull(expireAt, "运行到期时间不能为空");
        if (createTime.isAfter(startedAt) || updateTime.isBefore(createTime) || expireAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("运行时间范围不合法");
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
