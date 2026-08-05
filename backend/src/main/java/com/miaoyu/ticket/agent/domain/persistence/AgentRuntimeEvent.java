package com.miaoyu.ticket.agent.domain.persistence;

import java.time.LocalDateTime;
import java.util.Objects;

/** 追加式的可展示运行事实；eventId 是 SSE 的十进制重放游标。 */
public record AgentRuntimeEvent(
        long eventId,
        String sessionId,
        String runId,
        AgentEventType type,
        AgentStoredJson payload,
        LocalDateTime expireAt,
        LocalDateTime createTime) {

    public AgentRuntimeEvent {
        if (eventId <= 0) {
            throw new IllegalArgumentException("事件 ID 必须为正数");
        }
        requireText(sessionId, "sessionId");
        requireText(runId, "runId");
        Objects.requireNonNull(type, "事件类型不能为空");
        Objects.requireNonNull(payload, "事件载荷不能为空");
        expireAt = Objects.requireNonNull(expireAt, "事件到期时间不能为空");
        createTime = Objects.requireNonNull(createTime, "事件创建时间不能为空");
        if (expireAt.isBefore(createTime)) {
            throw new IllegalArgumentException("事件到期时间不能早于创建时间");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
    }
}
