package com.miaoyu.ticket.agent.application.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.model.AgentConversationContext;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentQuestionAnswerMessagePersistenceTest {

    @Test
    void shouldPersistQuestionEntryOnUserMessageForHistoryProjection() {
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentRuntimeEventService events = mock(AgentRuntimeEventService.class);
        AgentConversationSlotService slots = mock(AgentConversationSlotService.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 8, 10, 0);
        AgentSession session = new AgentSession(10L, "session-1", 9L, null, AgentSessionStatus.ACTIVE,
                null, 0L, now, now, now.plusDays(30));
        SlotSnapshot snapshot = new SlotSnapshot(2L, Map.of("date", "2026-08-09"));
        when(sessions.findBySessionIdAndUserIdForUpdate("session-1", 9L)).thenReturn(Optional.of(session));
        when(runs.findByClientRequestId(9L, 10L, "request-1")).thenReturn(Optional.empty());
        when(slots.prepareLocked(session, 9L, "明天", "question"))
                .thenReturn(new AgentConversationSlotService.PreparedSlots(snapshot,
                        new AgentConversationContext("我想看长沙的蜘蛛侠", null), false, "{}"));
        when(sessions.claimActiveRun(any(Long.class), any(Long.class), any(Long.class), any()))
                .thenReturn(true);
        BusinessIdGenerator ids = new BusinessIdGenerator() {
            private long next = 100L;

            @Override
            public long nextId() {
                return next++;
            }
        };
        AgentInitialRunTransaction transaction = new AgentInitialRunTransaction(
                sessions, runs, messages, new AgentRequestHashFactory(), events, slots, ids,
                Clock.fixed(Instant.parse("2026-08-08T02:00:00Z"), ZoneId.of("Asia/Shanghai")));

        transaction.submitConversation(9L, "session-1", "明天", "request-1", "question");

        ArgumentCaptor<AgentMessage> captured = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messages).insert(captured.capture());
        assertThat(captured.getValue().payload()).isNotNull();
        assertThat(captured.getValue().payload().value()).isEqualTo("{\"entry\":\"question\"}");
    }
}
