package com.miaoyu.ticket.agent.domain.persistence;

import java.time.LocalDateTime;
import java.util.Objects;

/** 待追加事件；数据库提交后才会分配 eventId。 */
public record AgentRuntimeEventDraft(
        String sessionId,
        String runId,
        AgentEventType type,
        AgentStoredJson payload,
        LocalDateTime expireAt,
        LocalDateTime createTime) {

    public AgentRuntimeEventDraft {
        if (sessionId == null || sessionId.isBlank() || runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("会话或运行标识不能为空");
        }
        Objects.requireNonNull(type, "事件类型不能为空");
        Objects.requireNonNull(payload, "事件载荷不能为空");
        expireAt = Objects.requireNonNull(expireAt, "事件到期时间不能为空");
        createTime = Objects.requireNonNull(createTime, "事件创建时间不能为空");
        if (expireAt.isBefore(createTime)) {
            throw new IllegalArgumentException("事件到期时间不能早于创建时间");
        }
    }
}
