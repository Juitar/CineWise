package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventStreamCursor;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEventDraft;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;

/** 载荷不合法时必须在原事实事务写入前失败。 */
class AgentRuntimeEventServiceTest {

    @Test
    void shouldRejectNonObjectAndOversizedPayloadBeforeRepositoryAccess() {
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        AgentRuntimeEventRepository eventRepository = Mockito.mock(AgentRuntimeEventRepository.class);
        AgentRuntimeEventService service = new AgentRuntimeEventService(
                sessionRepository, runRepository, eventRepository, new ObjectMapper());

        assertThatThrownBy(() -> service.append(null, null, AgentEventType.MESSAGE_START, new AgentStoredJson("[]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON 对象");
        String oversized = "{\"text\":\"" + "x".repeat(16 * 1024) + "\"}";
        assertThatThrownBy(() -> service.append(null, null, AgentEventType.MESSAGE_START,
                new AgentStoredJson(oversized)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16 KiB");
        assertThatThrownBy(() -> service.append(null, null, AgentEventType.MESSAGE_START,
                new AgentStoredJson("{\"token\":\"secret\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("禁止字段");

        verifyNoInteractions(sessionRepository, runRepository, eventRepository);
    }

    @Test
    void shouldKeepCursorUntilTheLatestSessionOrRunExpiry() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 0);
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        AgentRuntimeEventRepository eventRepository = Mockito.mock(AgentRuntimeEventRepository.class);
        AgentRuntimeEventService service = new AgentRuntimeEventService(
                sessionRepository, runRepository, eventRepository, new ObjectMapper());
        AgentSession session = new AgentSession(1L, "session-1", 9L, null, AgentSessionStatus.ACTIVE, 2L,
                0L, now.minusDays(1), now, now.plusDays(1));
        AgentRun run = new AgentRun(2L, "run-1", 1L, 9L, "request-1", new AgentRequestHash("v1",
                "0".repeat(64)),
                null, null, AgentRunStatus.RUNNING, "trace", now, null, 0L, now, now, now.plusDays(30));
        AgentEventStreamCursor persistedCursor = new AgentEventStreamCursor("session-1", 0L, null, 0L,
                now.plusDays(30), now, now);
        AgentRuntimeEvent event = new AgentRuntimeEvent(7L, "session-1", "run-1", AgentEventType.MESSAGE_START,
                new AgentStoredJson("{\"phase\":\"accepted\"}"), now.plusDays(30), now);
        when(sessionRepository.findBySessionIdAndUserIdForUpdate("session-1", 9L)).thenReturn(Optional.of(session));
        when(runRepository.findByRunIdAndUserId("run-1", 9L)).thenReturn(Optional.of(run));
        when(eventRepository.findCursorForUpdate("session-1"))
                .thenReturn(Optional.empty(), Optional.of(persistedCursor));
        when(eventRepository.append(any())).thenReturn(event);
        when(eventRepository.updateCursor(any(), Mockito.eq(0L))).thenReturn(true);

        service.append(session, run, AgentEventType.MESSAGE_START, new AgentStoredJson("{\"phase\":\"accepted\"}"));

        ArgumentCaptor<AgentEventStreamCursor> cursorCaptor = ArgumentCaptor.forClass(AgentEventStreamCursor.class);
        Mockito.verify(eventRepository).insertCursor(cursorCaptor.capture());
        assertThat(cursorCaptor.getValue().expireAt()).isEqualTo(run.expireAt());
    }

    @Test
    void shouldNotMoveCursorTimeBackwardWhenConcurrentRequestOwnsAnEarlierRunTime() {
        LocalDateTime runTime = LocalDateTime.of(2026, 8, 5, 10, 0);
        LocalDateTime cursorTime = runTime.plusNanos(1_000_000);
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        AgentRuntimeEventRepository eventRepository = Mockito.mock(AgentRuntimeEventRepository.class);
        AgentRuntimeEventService service = new AgentRuntimeEventService(
                sessionRepository, runRepository, eventRepository, new ObjectMapper());
        AgentSession session = new AgentSession(1L, "session-1", 9L, null, AgentSessionStatus.ACTIVE, 2L,
                0L, runTime.minusDays(1), cursorTime, runTime.plusDays(30));
        AgentRun run = new AgentRun(2L, "run-1", 1L, 9L, "request-1", new AgentRequestHash("v1",
                "0".repeat(64)), null, null, AgentRunStatus.RUNNING, "trace", runTime, null, 0L,
                runTime, runTime, runTime.plusDays(30));
        AgentEventStreamCursor cursor = new AgentEventStreamCursor("session-1", 8L, 4L, 2L,
                runTime.plusDays(30), runTime, cursorTime);
        AgentRuntimeEvent event = new AgentRuntimeEvent(9L, "session-1", "run-1", AgentEventType.MESSAGE_START,
                new AgentStoredJson("{\"phase\":\"accepted\"}"), runTime.plusDays(30), cursorTime);
        when(sessionRepository.findBySessionIdAndUserIdForUpdate("session-1", 9L)).thenReturn(Optional.of(session));
        when(runRepository.findByRunIdAndUserId("run-1", 9L)).thenReturn(Optional.of(run));
        when(eventRepository.findCursorForUpdate("session-1")).thenReturn(Optional.of(cursor));
        when(eventRepository.append(any())).thenReturn(event);
        when(eventRepository.updateCursor(any(), Mockito.eq(2L))).thenReturn(true);

        service.append(session, run, AgentEventType.MESSAGE_START, new AgentStoredJson("{\"phase\":\"accepted\"}"));

        ArgumentCaptor<AgentRuntimeEventDraft> draftCaptor = ArgumentCaptor.forClass(AgentRuntimeEventDraft.class);
        ArgumentCaptor<AgentEventStreamCursor> cursorCaptor = ArgumentCaptor.forClass(AgentEventStreamCursor.class);
        Mockito.verify(eventRepository).append(draftCaptor.capture());
        Mockito.verify(eventRepository).updateCursor(cursorCaptor.capture(), Mockito.eq(2L));
        assertThat(draftCaptor.getValue().createTime()).isEqualTo(cursorTime);
        assertThat(cursorCaptor.getValue().updateTime()).isEqualTo(cursorTime);
    }

    @Test
    void shouldRejectLateEventFromSupersededPlanBeforeWritingEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 0);
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        AgentRuntimeEventRepository eventRepository = Mockito.mock(AgentRuntimeEventRepository.class);
        AgentRuntimeEventService service = new AgentRuntimeEventService(
                sessionRepository, runRepository, eventRepository, new ObjectMapper());
        AgentRun stale = new AgentRun(2L, "run-1", 1L, 9L, "request-1", new AgentRequestHash("v1",
                "0".repeat(64)), "plan-1", 1, AgentRunStatus.RUNNING, "trace", now, null, 0L,
                now, now, now.plusDays(30));
        AgentRun current = new AgentRun(2L, "run-1", 1L, 9L, "request-1", new AgentRequestHash("v1",
                "0".repeat(64)), "plan-2", 2, AgentRunStatus.RUNNING, "trace", now, null, 1L,
                now, now, now.plusDays(30));
        when(runRepository.findByRunIdAndUserId("run-1", 9L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.append(null, stale, AgentEventType.TOOL_RESULT,
                new AgentStoredJson("{\"status\":\"SUCCESS\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("计划已更新");

        verifyNoInteractions(sessionRepository, eventRepository);
    }
}
