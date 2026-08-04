package com.miaoyu.ticket.agent.domain.persistence;

import java.time.LocalDateTime;
import java.util.Objects;

/** 一次写入的用户或助手展示消息；V008 不保存流式中间消息。 */
public record AgentMessage(
        long id,
        String messageId,
        long sessionId,
        long runId,
        long userId,
        AgentMessageRole role,
        AgentMessageType type,
        String text,
        AgentStoredJson payload,
        AgentMessageStatus status,
        LocalDateTime completedAt,
        LocalDateTime createTime,
        LocalDateTime expireAt) {

    public AgentMessage {
        requirePositive(id, "消息内部 ID");
        requireText(messageId, "messageId");
        requirePositive(sessionId, "会话内部 ID");
        requirePositive(runId, "运行内部 ID");
        requirePositive(userId, "用户 ID");
        Objects.requireNonNull(role, "消息角色不能为空");
        Objects.requireNonNull(type, "消息类型不能为空");
        if (role == AgentMessageRole.USER && type != AgentMessageType.TEXT) {
            throw new IllegalArgumentException("用户消息只能使用 TEXT 类型");
        }
        requireText(text, "消息文本");
        Objects.requireNonNull(status, "消息状态不能为空");
        completedAt = Objects.requireNonNull(completedAt, "消息完成时间不能为空");
        createTime = Objects.requireNonNull(createTime, "消息创建时间不能为空");
        expireAt = Objects.requireNonNull(expireAt, "消息到期时间不能为空");
        if (completedAt.isBefore(createTime) || expireAt.isBefore(createTime)) {
            throw new IllegalArgumentException("消息时间范围不合法");
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
