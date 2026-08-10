package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.persistence.AgentConversationSlotRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentConversationSlotService;
import com.miaoyu.ticket.agent.application.AgentCityCodeResolver;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.application.tool.MovieGenreResolutionTool;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageRole;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.content.application.CityResolutionService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

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
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-07T02:00:00Z"), ZoneId.of("Asia/Shanghai")),
                cityResolver());

        var snapshot = service.prepare("session-1", "2026-08-08", "workspace");

        assertThat(snapshot.values()).containsEntry("date", "2026-08-08").containsEntry("context.entry", "workspace");
        verify(slots).update(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.contains("2026-08-08"));
    }

    @Test
    void shouldKeepSlotsWhenTheTicketCountIsTooLong() {
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
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-07T02:00:00Z"), ZoneId.of("Asia/Shanghai")),
                cityResolver());

        var snapshot = service.prepare("session-1", "999999999999999999999999999999", null);

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
        when(messages.findBySessionIdAndUserId(10L, 9L, 1)).thenReturn(List.of(questionKind("CITY", now)));
        when(slots.findBySessionIdAndUserId("session-1", 9L))
                .thenReturn(Optional.of("{\"version\":4,\"values\":{\"date\":\"2026-08-06\"}}"));
        when(slots.update(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        AgentConversationSlotService service = service(users, sessions, messages, slots);

        var city = service.prepare("session-1", "长沙", null);

        assertThat(city.values()).containsOnlyKeys("cityCode").containsEntry("cityCode", "430100");
        when(messages.findBySessionIdAndUserId(10L, 9L, 1)).thenReturn(List.of(questionKind("TICKET_COUNT", now)));
        when(slots.findBySessionIdAndUserId("session-1", 9L))
                .thenReturn(Optional.of("{\"version\":5,\"values\":{\"cityCode\":\"430100\"}}"));

        var tickets = service.prepare("session-1", "2", null);

        assertThat(tickets.values()).containsEntry("cityCode", "430100").containsEntry("ticketCount", "2");
    }

    @Test
    void shouldNormalizeRelativeDateAndNaturalTicketCountAnswers() {
        assertThat(prepareAnswer("DATE", "明天啊", persistedContext()).values())
                .containsEntry("date", "2026-08-08");
        assertThat(prepareAnswer("DATE", "后天", persistedContext()).values())
                .containsEntry("date", "2026-08-09");
        assertThat(prepareAnswer("DATE", "周六", persistedContext()).values())
                .containsEntry("date", "2026-08-08");
        assertThat(prepareAnswer("TICKET_COUNT", "两个人", persistedContext()).values())
                .containsEntry("ticketCount", "2");
        assertThat(prepareAnswer("TICKET_COUNT", "2人", persistedContext()).values())
                .containsEntry("ticketCount", "2");
        assertThat(prepareAnswer("TICKET_COUNT", "一个", persistedContext()).values())
                .containsEntry("ticketCount", "1");
        assertThat(prepareAnswer("TICKET_COUNT", "1个", persistedContext()).values())
                .containsEntry("ticketCount", "1");
        assertThat(prepareAnswer("TICKET_COUNT", "two people", persistedContext()).values())
                .containsEntry("ticketCount", "2");
        assertThat(prepareAnswer("TICKET_COUNT", "两张票吧", persistedContext()).values())
                .containsEntry("ticketCount", "2");
    }

    @Test
    void shouldNormalizeCommonExactAndWeekendDateAnswersWithoutRepeatedQuestions() {
        assertThat(prepareAnswer("DATE", "今天", persistedContext()).values())
                .containsEntry("date", "2026-08-07");
        assertThat(prepareAnswer("DATE", "今晚", persistedContext()).values())
                .containsEntry("date", "2026-08-07");
        assertThat(prepareAnswer("DATE", "8.8", persistedContext()).values())
                .containsEntry("date", "2026-08-08");
        assertThat(prepareAnswer("DATE", "8月8日", persistedContext()).values())
                .containsEntry("date", "2026-08-08");
        assertThat(prepareAnswer("DATE", "八月八号", persistedContext()).values())
                .containsEntry("date", "2026-08-08");
        assertThat(prepareAnswer("DATE", "2026-08-08", persistedContext()).values())
                .containsEntry("date", "2026-08-08");
        assertThat(prepareAnswer("DATE", "本周周末", persistedContext()).values())
                .containsEntry("date", "2026-08-08");
        assertThat(prepareAnswer("DATE", "下周周末", persistedContext()).values())
                .containsEntry("date", "2026-08-15");
        assertThat(prepareAnswer("DATE", "我明天和两个人一起看", persistedContext()).values())
                .containsEntry("date", "2026-08-08")
                .containsEntry("ticketCount", "3");
        assertThat(prepareAnswer("DATE", "我今天和一个人一起看", persistedContext()).values())
                .containsEntry("date", "2026-08-07")
                .containsEntry("ticketCount", "2");
        assertThat(prepareAnswer("DATE", "我想看惊悚的", persistedContext()).values())
                .containsEntry("genres", "[\"惊悚\"]");
    }

    @Test
    void shouldRejectPastExactDateAnswers() {
        assertThat(prepareAnswer("DATE", "8.6", persistedContext()).values())
                .doesNotContainKey("date");
        assertThat(prepareAnswer("DATE", "2026-08-06", persistedContext()).values())
                .doesNotContainKey("date");
    }

    @Test
    void shouldResolveCityTextFromACompleteMovieRequestButNeverAcceptARawCityCode() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentConversationSlotRepository slots = mock(AgentConversationSlotRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 10, 0);
        AgentSession session = new AgentSession(10L, "session-1", 9L, null, AgentSessionStatus.ACTIVE, null,
                0L, now, now, now.plusDays(30));
        when(users.requireCurrentUserId()).thenReturn(9L);
        when(sessions.findBySessionIdAndUserIdForUpdate("session-1", 9L)).thenReturn(Optional.of(session));
        when(messages.findBySessionIdAndUserId(10L, 9L, 1)).thenReturn(List.of());
        when(slots.findBySessionIdAndUserId("session-1", 9L)).thenReturn(Optional.of("{}"));
        when(slots.update(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        AgentConversationSlotService service = service(users, sessions, messages, slots);

        var resolved = service.prepare("session-1", "我想看长沙的动作片", null);

        assertThat(resolved.values())
                .containsOnlyKeys("cityCode", "genres")
                .containsEntry("cityCode", "430100")
                .containsEntry("genres", "[\"动作\"]");
        var persisted = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(slots).update(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(0L), persisted.capture());
        assertThat(persisted.getValue()).contains("originalRequest", "我想看长沙的动作片");
        when(slots.findBySessionIdAndUserId("session-1", 9L)).thenReturn(Optional.of("{}"));

        var rawCode = service.prepare("session-1", "430100", null);

        assertThat(rawCode.values()).isEmpty();
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
        org.mockito.Mockito.verify(slots, org.mockito.Mockito.never())
                .update(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    private static AgentConversationSlotService service(CurrentUserAccessor users, AgentSessionRepository sessions,
            AgentMessageRepository messages, AgentConversationSlotRepository slots) {
        MovieGenreResolutionTool genres = mock(MovieGenreResolutionTool.class);
        when(genres.resolve(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String input = invocation.getArgument(0, String.class);
            return List.of("动作", "惊悚").stream().filter(input::contains).toList();
        });
        return new AgentConversationSlotService(users, sessions, messages, slots, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-07T02:00:00Z"), ZoneId.of("Asia/Shanghai")), cityResolver(),
                null, null, genres);
    }

    private static AgentCityCodeResolver cityResolver() {
        CityResolutionService cityResolutionService = new CityResolutionService(
                new ObjectMapper(), new DefaultResourceLoader());
        return text -> {
            CityResolutionService.CityResolution resolved = cityResolutionService.resolve(text);
            return resolved.status() == CityResolutionService.Status.RESOLVED
                    ? cityResolutionService.findCityCode(resolved.cityName())
                    : java.util.Optional.empty();
        };
    }

    private static SlotSnapshot prepareAnswer(String kind, String answer, String persistedJson) {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentConversationSlotRepository slots = mock(AgentConversationSlotRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 10, 0);
        AgentSession session = new AgentSession(10L, "session-1", 9L, null, AgentSessionStatus.ACTIVE, null,
                0L, now, now, now.plusDays(30));
        when(users.requireCurrentUserId()).thenReturn(9L);
        when(sessions.findBySessionIdAndUserIdForUpdate("session-1", 9L)).thenReturn(Optional.of(session));
        when(messages.findBySessionIdAndUserId(10L, 9L, 1)).thenReturn(List.of(questionKind(kind, now)));
        when(slots.findBySessionIdAndUserId("session-1", 9L)).thenReturn(Optional.of(persistedJson));
        when(slots.update(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        return service(users, sessions, messages, slots).prepare("session-1", answer, null);
    }

    private static String persistedContext() {
        return "{\"version\":1,\"values\":{\"cityCode\":\"430100\"},"
                + "\"context\":{\"originalRequest\":\"我想看长沙蜘蛛侠\"}}";
    }

    private static AgentMessage question(String slot, LocalDateTime now) {
        return new AgentMessage(11L, "message-1", 10L, 12L, 9L, AgentMessageRole.ASSISTANT,
                AgentMessageType.QUESTION, "请补充", new AgentStoredJson("{\"missingSlot\":\"" + slot + "\"}"),
                AgentMessageStatus.COMPLETED, now, now, now.plusDays(30));
    }

    private static AgentMessage questionKind(String kind, LocalDateTime now) {
        return new AgentMessage(11L, "message-1", 10L, 12L, 9L, AgentMessageRole.ASSISTANT,
                AgentMessageType.QUESTION, "请补充", new AgentStoredJson("{\"questionKind\":\"" + kind + "\"}"),
                AgentMessageStatus.COMPLETED, now, now, now.plusDays(30));
    }
}
