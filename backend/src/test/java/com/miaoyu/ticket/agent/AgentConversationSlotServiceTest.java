package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.persistence.AgentConversationSlotRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentConversationSlotService;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageRole;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentConversationSlotServiceTest {
    @Test
    void shouldSaveOnlyTheValidatedAnswerForTheLatestServerQuestion() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentConversationSlotRepository slots = mock(AgentConversationSlotRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 10, 0);
        AgentSession session = new AgentSession(10L, "session-1", 9L, null, AgentSessionStatus.ACTIVE, null,
                0L, now, now, now.plusDays(30));
        AgentMessage question = new AgentMessage(11L, "message-1", 10L, 12L, 9L, AgentMessageRole.ASSISTANT,
                AgentMessageType.QUESTION, "请补充日期", new AgentStoredJson("{\"missingSlot\":\"date\"}"),
                AgentMessageStatus.COMPLETED, now, now, now.plusDays(30));
        when(users.requireCurrentUserId()).thenReturn(9L);
        when(sessions.findBySessionIdAndUserIdForUpdate("session-1", 9L)).thenReturn(Optional.of(session));
        when(slots.findBySessionIdAndUserId("session-1", 9L)).thenReturn(Optional.of("{}"));
        when(slots.update(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(messages.findBySessionIdAndUserId(10L, 9L, 1)).thenReturn(List.of(question));
        AgentConversationSlotService service = new AgentConversationSlotService(users, sessions, messages, slots,
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-07T02:00:00Z"), ZoneId.of("Asia/Shanghai")));

        var snapshot = service.prepare("session-1", "2026-08-08", "workspace");

        assertThat(snapshot.values()).containsEntry("date", "2026-08-08").containsEntry("context.entry", "workspace");
        verify(slots).update(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.contains("2026-08-08"));
    }

    @Test
    void shouldKeepSlotsWhenTheAnswerIsInvalid() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentConversationSlotRepository slots = mock(AgentConversationSlotRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 10, 0);
        AgentSession session = new AgentSession(10L, "session-1", 9L, null, AgentSessionStatus.ACTIVE, null,
                0L, now, now, now.plusDays(30));
        AgentMessage question = new AgentMessage(11L, "message-1", 10L, 12L, 9L, AgentMessageRole.ASSISTANT,
                AgentMessageType.QUESTION, "请补充票数", new AgentStoredJson("{\"missingSlot\":\"ticketCount\"}"),
                AgentMessageStatus.COMPLETED, now, now, now.plusDays(30));
        when(users.requireCurrentUserId()).thenReturn(9L);
        when(sessions.findBySessionIdAndUserIdForUpdate("session-1", 9L)).thenReturn(Optional.of(session));
        when(slots.findBySessionIdAndUserId("session-1", 9L))
                .thenReturn(Optional.of("{\"version\":2,\"values\":{\"cityCode\":\"430100\"}}"));
        when(messages.findBySessionIdAndUserId(10L, 9L, 1)).thenReturn(List.of(question));
        AgentConversationSlotService service = new AgentConversationSlotService(users, sessions, messages, slots,
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-07T02:00:00Z"), ZoneId.of("Asia/Shanghai")));

        var snapshot = service.prepare("session-1", "0", null);

        assertThat(snapshot.values()).containsOnlyKeys("cityCode").containsEntry("cityCode", "430100");
        org.mockito.Mockito.verify(slots, org.mockito.Mockito.never())
                .update(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldSaveCityAndTicketCountAndDiscardAnExpiredDateFromTheSnapshot() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentConversationSlotRepository slots = mock(AgentConversationSlotRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 10, 0);
        AgentSession session = new AgentSession(10L, "session-1", 9L, null, AgentSessionStatus.ACTIVE, null,
                0L, now, now, now.plusDays(30));
        when(users.requireCurrentUserId()).thenReturn(9L);
        when(sessions.findBySessionIdAndUserIdForUpdate("session-1", 9L)).thenReturn(Optional.of(session));
        when(messages.findBySessionIdAndUserId(10L, 9L, 1)).thenReturn(List.of(question("cityCode", now)));
        when(slots.findBySessionIdAndUserId("session-1", 9L))
                .thenReturn(Optional.of("{\"version\":4,\"values\":{\"date\":\"2026-08-06\"}}"));
        when(slots.update(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        AgentConversationSlotService service = service(users, sessions, messages, slots);

        var city = service.prepare("session-1", "430100", null);

        assertThat(city.values()).containsOnlyKeys("cityCode").containsEntry("cityCode", "430100");
        when(messages.findBySessionIdAndUserId(10L, 9L, 1)).thenReturn(List.of(question("ticketCount", now)));
        when(slots.findBySessionIdAndUserId("session-1", 9L))
                .thenReturn(Optional.of("{\"version\":5,\"values\":{\"cityCode\":\"430100\"}}"));

        var tickets = service.prepare("session-1", "2", null);

        assertThat(tickets.values()).containsEntry("cityCode", "430100").containsEntry("ticketCount", "2");
    }

    @Test
    void shouldRejectExpiredOrClearedSessionsBeforeReadingSlots() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentConversationSlotRepository slots = mock(AgentConversationSlotRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 10, 0);
        AgentSession expired = new AgentSession(10L, "session-1", 9L, null, AgentSessionStatus.ACTIVE, null,
                0L, now.minusDays(30), now.minusDays(1), now.minusSeconds(1));
        when(users.requireCurrentUserId()).thenReturn(9L);
        when(sessions.findBySessionIdAndUserIdForUpdate("session-1", 9L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service(users, sessions, messages, slots).prepare("session-1", "430100", null))
                .isInstanceOf(com.miaoyu.ticket.common.error.BusinessException.class);
        org.mockito.Mockito.verifyNoInteractions(messages, slots);
    }

    @Test
    void shouldRejectAnActiveRunBeforeChangingConversationSlots() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentConversationSlotRepository slots = mock(AgentConversationSlotRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 10, 0);
        AgentSession session = new AgentSession(10L, "session-1", 9L, null, AgentSessionStatus.ACTIVE, 99L,
                0L, now, now, now.plusDays(30));
        when(users.requireCurrentUserId()).thenReturn(9L);
        when(sessions.findBySessionIdAndUserIdForUpdate("session-1", 9L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service(users, sessions, messages, slots).prepare("session-1", "430100", null))
                .isInstanceOf(com.miaoyu.ticket.common.error.BusinessException.class)
                .hasMessageContaining("活动运行");
        org.mockito.Mockito.verifyNoInteractions(messages, slots);
    }

    private static AgentConversationSlotService service(CurrentUserAccessor users, AgentSessionRepository sessions,
            AgentMessageRepository messages, AgentConversationSlotRepository slots) {
        return new AgentConversationSlotService(users, sessions, messages, slots, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-07T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    private static AgentMessage question(String slot, LocalDateTime now) {
        return new AgentMessage(11L, "message-1", 10L, 12L, 9L, AgentMessageRole.ASSISTANT,
                AgentMessageType.QUESTION, "请补充", new AgentStoredJson("{\"missingSlot\":\"" + slot + "\"}"),
                AgentMessageStatus.COMPLETED, now, now, now.plusDays(30));
    }
}
