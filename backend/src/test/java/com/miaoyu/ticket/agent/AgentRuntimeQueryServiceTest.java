package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeQueryService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 运行详情的最后游标必须直接来自 run_id + event_id 索引，而不是当前页事件列表。 */
class AgentRuntimeQueryServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 10, 0);

    @Test
    void shouldUseRunEventWatermarkEvenWhenEventListIsEmpty() {
        CurrentUserAccessor currentUserAccessor = Mockito.mock(CurrentUserAccessor.class);
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        AgentMessageRepository messageRepository = Mockito.mock(AgentMessageRepository.class);
        AgentRunStepRepository stepRepository = Mockito.mock(AgentRunStepRepository.class);
        AgentRuntimeEventRepository eventRepository = Mockito.mock(AgentRuntimeEventRepository.class);
        when(currentUserAccessor.requireCurrentUserId()).thenReturn(7L);
        when(runRepository.findByRunIdAndUserId("run-1", 7L)).thenReturn(Optional.of(run()));
        when(sessionRepository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(session()));
        when(messageRepository.findByRunIdAndUserId(1L, 7L)).thenReturn(List.of());
        when(stepRepository.findByRunId(1L)).thenReturn(List.of());
        when(eventRepository.findByRunId("run-1", 0L, 500)).thenReturn(List.of());
        when(eventRepository.findLastEventIdByRunId("run-1")).thenReturn(901L);
        AgentRuntimeQueryService service = new AgentRuntimeQueryService(currentUserAccessor, runRepository,
                sessionRepository, messageRepository, stepRepository, eventRepository);

        assertThat(service.queryMyRun("run-1").lastEventId()).isEqualTo(901L);

        verify(eventRepository).findLastEventIdByRunId("run-1");
    }

    private static AgentRun run() {
        return new AgentRun(1L, "run-1", 1L, 7L, "request-1", new AgentRequestHash("v1", "a".repeat(64)),
                null, null, AgentRunStatus.RUNNING, "trace-1", NOW, null, 0L, NOW, NOW, NOW.plusDays(30));
    }

    private static AgentSession session() {
        return new AgentSession(1L, "session-1", 7L, null, AgentSessionStatus.ACTIVE, 1L, 0L,
                NOW, NOW, NOW.plusDays(30));
    }
}
