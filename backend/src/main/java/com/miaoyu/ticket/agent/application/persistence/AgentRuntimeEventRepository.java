package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentEventStreamCursor;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEventDraft;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

/** B 自有事件存储端口；调用方必须在事务中先锁会话再锁游标。 */
public interface AgentRuntimeEventRepository {

    Optional<AgentEventStreamCursor> findCursorForUpdate(String sessionId);

    Optional<AgentEventStreamCursor> findCursor(String sessionId);

    void insertCursor(AgentEventStreamCursor cursor);

    boolean updateCursor(AgentEventStreamCursor cursor, long expectedVersion);

    AgentRuntimeEvent append(AgentRuntimeEventDraft draft);

    List<AgentRuntimeEvent> findBySessionAfter(String sessionId, long eventId, int limit);

    List<AgentRuntimeEvent> findByRunId(String runId, long eventId, int limit);

    long findLastEventIdByRunId(String runId);

    boolean existsBySessionAndEventId(String sessionId, long eventId);

    /** 返回已到期且运行已终态的事件所属运行；同一运行只返回一次，最多处理一个固定批次。 */
    List<AgentExpiredRunCandidate> findExpiredTerminalRuns(LocalDateTime now, int limit);

    /** 删除一条终态运行的所有事件；调用方必须已经锁定所属会话与游标。 */
    int deleteByRunId(String runId);

    Long findFirstRetainedEventId(String sessionId);

    void deleteCursor(String sessionId);
}
