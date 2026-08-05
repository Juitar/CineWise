package com.miaoyu.ticket.agent.infrastructure.persistence;

import java.time.LocalDateTime;

/** `agent_event_stream_cursor` 的可更新游标行。 */
public record AgentEventStreamCursorEntity(
        String sessionId,
        long lastCommittedEventId,
        Long firstRetainedEventId,
        long version,
        LocalDateTime expireAt,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
