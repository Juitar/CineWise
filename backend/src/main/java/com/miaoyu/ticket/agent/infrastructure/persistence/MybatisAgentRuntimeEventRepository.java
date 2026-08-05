package com.miaoyu.ticket.agent.infrastructure.persistence;

import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentExpiredRunCandidate;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventStreamCursor;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEventDraft;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 事件追加和游标读取实现；LAST_INSERT_ID 只在同一事务连接中使用。 */
@Repository
public class MybatisAgentRuntimeEventRepository implements AgentRuntimeEventRepository {
    private final AgentPersistenceMapper mapper;

    public MybatisAgentRuntimeEventRepository(AgentPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AgentEventStreamCursor> findCursorForUpdate(String sessionId) {
        return Optional.ofNullable(mapper.findEventCursorForUpdate(sessionId)).map(AgentPersistenceMappings::toDomain);
    }

    @Override
    public Optional<AgentEventStreamCursor> findCursor(String sessionId) {
        return Optional.ofNullable(mapper.findEventCursor(sessionId)).map(AgentPersistenceMappings::toDomain);
    }

    @Override
    public void insertCursor(AgentEventStreamCursor cursor) {
        mapper.insertEventCursor(AgentPersistenceMappings.toEntity(cursor));
    }

    @Override
    public boolean updateCursor(AgentEventStreamCursor cursor, long expectedVersion) {
        return mapper.updateEventCursor(AgentPersistenceMappings.toEntity(cursor), expectedVersion) == 1;
    }

    @Override
    public AgentRuntimeEvent append(AgentRuntimeEventDraft draft) {
        mapper.insertRuntimeEvent(AgentPersistenceMappings.toEntity(0L, draft));
        long eventId = mapper.lastInsertedEventId();
        return new AgentRuntimeEvent(eventId, draft.sessionId(), draft.runId(), draft.type(), draft.payload(),
                draft.expireAt(), draft.createTime());
    }

    @Override
    public List<AgentRuntimeEvent> findBySessionAfter(String sessionId, long eventId, int limit) {
        return mapper.findRuntimeEventsBySessionAfter(sessionId, eventId, limit).stream()
                .map(AgentPersistenceMappings::toDomain).toList();
    }

    @Override
    public List<AgentRuntimeEvent> findByRunId(String runId, long eventId, int limit) {
        return mapper.findRuntimeEventsByRunId(runId, eventId, limit).stream()
                .map(AgentPersistenceMappings::toDomain).toList();
    }

    @Override
    public long findLastEventIdByRunId(String runId) {
        return mapper.findLastRuntimeEventIdByRunId(runId);
    }

    @Override
    public boolean existsBySessionAndEventId(String sessionId, long eventId) {
        return mapper.countRuntimeEventBySessionAndEventId(sessionId, eventId) == 1;
    }

    @Override
    public List<AgentExpiredRunCandidate> findExpiredTerminalRuns(LocalDateTime now, int limit) {
        return mapper.findExpiredTerminalRuns(now, limit).stream()
                .map(candidate -> new AgentExpiredRunCandidate(candidate.runId(), candidate.externalRunId(),
                        candidate.userId(), candidate.sessionId()))
                .toList();
    }

    @Override
    public int deleteByRunId(String runId) {
        return mapper.deleteRuntimeEventsByRunId(runId);
    }

    @Override
    public Long findFirstRetainedEventId(String sessionId) {
        return mapper.findFirstRuntimeEventIdBySession(sessionId);
    }

    @Override
    public void deleteCursor(String sessionId) {
        mapper.deleteEventCursor(sessionId);
    }
}
