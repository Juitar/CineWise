package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.persistence.AgentEventCleanupService;
import com.miaoyu.ticket.agent.application.persistence.AgentExpiredRunCandidate;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventStreamCursor;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 到期清理必须先删子记录，再删运行、游标和空会话。 */
class AgentEventCleanupServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 10, 0);

    @Test
    void shouldDeleteTerminalRunChildrenBeforeCursorAndEmptySession() {
        AgentRuntimeEventRepository eventRepository = Mockito.mock(AgentRuntimeEventRepository.class);
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        AgentRunStepRepository stepRepository = Mockito.mock(AgentRunStepRepository.class);
        AgentMessageRepository messageRepository = Mockito.mock(AgentMessageRepository.class);
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        PlatformTransactionManager transactionManager = transactionManager();
        AgentExpiredRunCandidate candidate = new AgentExpiredRunCandidate(11L, "run-1", 7L, "session-1");
        AgentSession session = new AgentSession(5L, "session-1", 7L, null, AgentSessionStatus.ACTIVE, null,
                0L, NOW, NOW, NOW.plusDays(30));
        AgentEventStreamCursor cursor = new AgentEventStreamCursor("session-1", 9L, 3L, 1L,
                NOW.plusDays(30), NOW, NOW);
        when(eventRepository.findExpiredTerminalRuns(NOW, 500)).thenReturn(List.of(candidate));
        when(sessionRepository.findBySessionIdAndUserIdForUpdate("session-1", 7L)).thenReturn(Optional.of(session));
        when(eventRepository.findCursorForUpdate("session-1")).thenReturn(Optional.of(cursor));
        when(runRepository.deleteTerminalExpiredById(11L, NOW)).thenReturn(true);
        when(runRepository.hasRuns(5L)).thenReturn(false);

        AgentEventCleanupService service = new AgentEventCleanupService(eventRepository, sessionRepository,
                stepRepository, messageRepository, runRepository,
                Clock.fixed(Instant.parse("2026-08-05T02:00:00Z"), ZoneOffset.UTC), transactionManager);

        assertThat(service.cleanupExpiredRuns()).isEqualTo(1);

        InOrder order = inOrder(eventRepository, stepRepository, messageRepository, runRepository, sessionRepository);
        order.verify(eventRepository).deleteByRunId("run-1");
        order.verify(stepRepository).deleteByRunId(11L);
        order.verify(messageRepository).deleteByRunId(11L);
        order.verify(runRepository).deleteTerminalExpiredById(11L, NOW);
        order.verify(eventRepository).deleteCursor("session-1");
        order.verify(sessionRepository).deleteIfEmptyAndInactive(5L);
    }

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = Mockito.mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(Mockito.any())).thenReturn(new SimpleTransactionStatus());
        return transactionManager;
    }
}
