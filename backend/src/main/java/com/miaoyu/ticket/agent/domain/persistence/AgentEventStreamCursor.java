package com.miaoyu.ticket.agent.domain.persistence;

import java.time.LocalDateTime;
import java.util.Objects;

/** 会话事件保留边界和已提交水位线；不是不可变事件事实。 */
public record AgentEventStreamCursor(
        String sessionId,
        long lastCommittedEventId,
        Long firstRetainedEventId,
        long version,
        LocalDateTime expireAt,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public AgentEventStreamCursor {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        if (lastCommittedEventId < 0 || version < 0) {
            throw new IllegalArgumentException("事件水位线或版本不能为负数");
        }
        if (firstRetainedEventId != null
                && (firstRetainedEventId <= 0 || firstRetainedEventId > lastCommittedEventId)) {
            throw new IllegalArgumentException("最早保留事件 ID 不合法");
        }
        expireAt = Objects.requireNonNull(expireAt, "游标到期时间不能为空");
        createTime = Objects.requireNonNull(createTime, "游标创建时间不能为空");
        updateTime = Objects.requireNonNull(updateTime, "游标更新时间不能为空");
        if (expireAt.isBefore(createTime) || updateTime.isBefore(createTime)) {
            throw new IllegalArgumentException("游标时间范围不合法");
        }
    }
}
