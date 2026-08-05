package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentEventStreamCursor;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 根据当前会话游标安全返回重放事件；不向客户端泄露其他会话的事件或数量。 */
@Service
public class AgentEventReplayService {
    private static final int EVENT_LIMIT = 500;
    private final AgentRuntimeEventRepository eventRepository;

    public AgentEventReplayService(AgentRuntimeEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public ReplayResult replay(String sessionId, long lastEventId) {
        if (lastEventId == 0L) {
            return new ReplayResult(eventRepository.findBySessionAfter(sessionId, 0L, EVENT_LIMIT), false, 0L);
        }
        AgentEventStreamCursor cursor = eventRepository.findCursor(sessionId).orElse(null);
        if (cursor == null || lastEventId > cursor.lastCommittedEventId()
                || cursor.firstRetainedEventId() == null || lastEventId < cursor.firstRetainedEventId()
                || !eventRepository.existsBySessionAndEventId(sessionId, lastEventId)) {
            return new ReplayResult(List.of(), true, cursor == null ? 0L : cursor.lastCommittedEventId());
        }
        return new ReplayResult(eventRepository.findBySessionAfter(sessionId, lastEventId, EVENT_LIMIT), false,
                cursor.lastCommittedEventId());
    }

    public record ReplayResult(List<AgentRuntimeEvent> events, boolean reset, long watermark) {
    }
}
