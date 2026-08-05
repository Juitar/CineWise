package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.persistence.AgentEventReplayService;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventStreamCursor;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 重放判定必须只依据当前会话游标，不能把其他会话的全局 ID 空洞当作保留事件。 */
class AgentEventReplayServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 10, 0);

    @Test
    void shouldTreatZeroAsStartSentinelWithoutReadingCursor() {
        AgentRuntimeEventRepository repository = Mockito.mock(AgentRuntimeEventRepository.class);
        when(repository.findBySessionAfter("session-1", 0L, 500)).thenReturn(List.of(event(4L)));

        var result = new AgentEventReplayService(repository).replay("session-1", 0L);

        assertThat(result.reset()).isFalse();
        assertThat(result.events()).extracting(AgentRuntimeEvent::eventId).containsExactly(4L);
        verify(repository).findBySessionAfter("session-1", 0L, 500);
    }

    @Test
    void shouldReplayOnlyEventsAfterRetainedCursor() {
        AgentRuntimeEventRepository repository = Mockito.mock(AgentRuntimeEventRepository.class);
        when(repository.findCursor("session-1")).thenReturn(Optional.of(cursor(9L, 4L)));
        when(repository.existsBySessionAndEventId("session-1", 6L)).thenReturn(true);
        when(repository.findBySessionAfter("session-1", 6L, 500)).thenReturn(List.of(event(9L)));

        var result = new AgentEventReplayService(repository).replay("session-1", 6L);

        assertThat(result.reset()).isFalse();
        assertThat(result.watermark()).isEqualTo(9L);
        assertThat(result.events()).extracting(AgentRuntimeEvent::eventId).containsExactly(9L);
    }

    @Test
    void shouldResetForCleanedUnknownOrFuturePositiveCursor() {
        AgentRuntimeEventRepository repository = Mockito.mock(AgentRuntimeEventRepository.class);
        when(repository.findCursor("session-1")).thenReturn(Optional.of(cursor(9L, 4L)));
        when(repository.existsBySessionAndEventId(eq("session-1"), anyLong())).thenReturn(false);
        AgentEventReplayService service = new AgentEventReplayService(repository);

        assertThat(service.replay("session-1", 3L).reset()).isTrue();
        assertThat(service.replay("session-1", 7L).reset()).isTrue();
        assertThat(service.replay("session-1", 10L).reset()).isTrue();
    }

    private static AgentEventStreamCursor cursor(long last, long first) {
        return new AgentEventStreamCursor("session-1", last, first, 0L, NOW.plusDays(30), NOW, NOW);
    }

    private static AgentRuntimeEvent event(long eventId) {
        return new AgentRuntimeEvent(eventId, "session-1", "run-1", AgentEventType.MESSAGE_START,
                new AgentStoredJson("{\"phase\":\"accepted\"}"), NOW.plusDays(30), NOW);
    }
}
