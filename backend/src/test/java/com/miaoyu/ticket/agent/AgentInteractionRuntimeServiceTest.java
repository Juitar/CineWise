package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.AgentInteractionRuntimeService;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationResult;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationService;
import com.miaoyu.ticket.agent.application.persistence.AgentEventReplayService;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionService;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeQueryService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunCancellationService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionCreationService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionManagementService;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentActionWriteIdentifiers;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Agent 运行时门面在失败连接中只能读取已持久化事件，不能重新提交请求。 */
class AgentInteractionRuntimeServiceTest {

    @Test
    void shouldReplayPersistedEventsWithoutSubmittingAnotherRun() {
        AgentMessageSubmissionService submissionService = mock(AgentMessageSubmissionService.class);
        AgentEventReplayService replayService = mock(AgentEventReplayService.class);
        AgentRuntimeQueryService queryService = mock(AgentRuntimeQueryService.class);
        AgentSessionCreationService sessionCreationService = mock(AgentSessionCreationService.class);
        AgentSessionManagementService sessionManagementService = mock(AgentSessionManagementService.class);
        AgentRunCancellationService runCancellationService = mock(AgentRunCancellationService.class);
        AgentConfirmationService confirmationService = mock(AgentConfirmationService.class);
        AgentInteractionRuntimeService service = new AgentInteractionRuntimeService(
                submissionService, replayService, queryService, sessionCreationService, sessionManagementService,
                runCancellationService, confirmationService, new ObjectMapper());
        LocalDateTime time = LocalDateTime.of(2026, 8, 5, 11, 20);
        AgentRuntimeEvent error = new AgentRuntimeEvent(8L, "session-1", "run-1", AgentEventType.MESSAGE_ERROR,
                new AgentStoredJson("{\"reason\":\"RUN_FAILED\"}"), time.plusDays(30), time);
        AgentRuntimeEvent complete = new AgentRuntimeEvent(9L, "session-1", "run-1", AgentEventType.RUN_COMPLETE,
                new AgentStoredJson("{\"status\":\"FAILED\"}"), time.plusDays(30), time);
        when(replayService.replay("session-1", 0L))
                .thenReturn(new AgentEventReplayService.ReplayResult(List.of(error, complete), false, 9L));

        var replay = service.replayPersistedEvents("session-1", 0L);

        assertThat(replay.runId()).isEqualTo("run-1");
        assertThat(replay.events()).extracting(AgentInteractionRuntimeService.EventView::eventType)
                .containsExactly("message.error", "run.complete");
        verify(replayService).replay("session-1", 0L);
        verifyNoInteractions(submissionService, queryService, sessionCreationService, sessionManagementService,
                runCancellationService, confirmationService);
    }

    @Test
    void shouldProjectTheLatestServerActionStatusWhenReplayingAnOldCard() {
        AgentMessageSubmissionService submissionService = mock(AgentMessageSubmissionService.class);
        AgentEventReplayService replayService = mock(AgentEventReplayService.class);
        AgentRuntimeQueryService queryService = mock(AgentRuntimeQueryService.class);
        AgentSessionCreationService sessionCreationService = mock(AgentSessionCreationService.class);
        AgentSessionManagementService sessionManagementService = mock(AgentSessionManagementService.class);
        AgentRunCancellationService runCancellationService = mock(AgentRunCancellationService.class);
        AgentConfirmationService confirmationService = mock(AgentConfirmationService.class);
        AgentInteractionRuntimeService service = new AgentInteractionRuntimeService(
                submissionService, replayService, queryService, sessionCreationService, sessionManagementService,
                runCancellationService, confirmationService, new ObjectMapper());
        LocalDateTime time = LocalDateTime.of(2026, 8, 5, 11, 20);
        AgentRuntimeEvent card = new AgentRuntimeEvent(8L, "session-1", "run-1", AgentEventType.CARD,
                new AgentStoredJson("""
                        {"actionId":"action-1","actionType":"CREATE_ORDER","status":"PENDING_CONFIRMATION",
                         "expireAt":"2026-08-05T11:25:00+08:00","displayTitle":"确认建单"}
                        """), time.plusDays(30), time);
        AgentConfirmationAction expired = AgentConfirmationAction.pending(
                1L, "action-1", 9L, 10L, 11L, "run-1", "plan-1", 1, "confirm-order",
                new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "4")), time.plusMinutes(5), time)
                .expire(time.plusMinutes(5));
        when(replayService.replay("session-1", 0L))
                .thenReturn(new AgentEventReplayService.ReplayResult(List.of(card), false, 8L));
        when(confirmationService.refreshForCurrentUser("action-1")).thenReturn(Optional.of(expired));

        var replay = service.replayPersistedEvents("session-1", 0L);

        assertThat(replay.events().getFirst().payload().path("status").asText()).isEqualTo("EXPIRED");
        assertThat(replay.events().getFirst().payload().path("expireAt").asText()).contains("+08:00");
        verify(confirmationService).refreshForCurrentUser("action-1");
    }

    @Test
    void shouldRecoverUnknownCardOnlyByOriginalIdentifiersWithoutConfirmingAgain() {
        AgentMessageSubmissionService submissionService = mock(AgentMessageSubmissionService.class);
        AgentEventReplayService replayService = mock(AgentEventReplayService.class);
        AgentRuntimeQueryService queryService = mock(AgentRuntimeQueryService.class);
        AgentSessionCreationService sessionCreationService = mock(AgentSessionCreationService.class);
        AgentSessionManagementService sessionManagementService = mock(AgentSessionManagementService.class);
        AgentRunCancellationService runCancellationService = mock(AgentRunCancellationService.class);
        AgentConfirmationService confirmationService = mock(AgentConfirmationService.class);
        AgentInteractionRuntimeService service = new AgentInteractionRuntimeService(
                submissionService, replayService, queryService, sessionCreationService, sessionManagementService,
                runCancellationService, confirmationService, new ObjectMapper());
        LocalDateTime time = LocalDateTime.of(2026, 8, 5, 11, 20);
        AgentRuntimeEvent card = new AgentRuntimeEvent(8L, "session-1", "run-1", AgentEventType.CARD,
                new AgentStoredJson("""
                        {"actionId":"action-1","actionType":"CREATE_ORDER","status":"RESULT_UNKNOWN",
                         "expireAt":"2026-08-05T11:25:00+08:00","displayTitle":"确认建单"}
                        """), time.plusDays(30), time);
        AgentConfirmationAction unknown = AgentConfirmationAction.pending(
                1L, "action-1", 9L, 10L, 11L, "run-1", "plan-1", 1, "confirm-order",
                new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "4")), time.plusMinutes(5), time)
                .claim(AgentActionWriteIdentifiers.forAction("action-1"), time.plusSeconds(1))
                .markResultUnknown("结果确认中", time.plusSeconds(2));
        AgentConfirmationAction succeeded = unknown.markSucceeded("order-1", time.plusSeconds(3));
        when(replayService.replay("session-1", 0L))
                .thenReturn(new AgentEventReplayService.ReplayResult(List.of(card), false, 8L));
        when(confirmationService.refreshForCurrentUser("action-1")).thenReturn(Optional.of(unknown));
        when(confirmationService.recover(org.mockito.ArgumentMatchers.eq("action-1"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new AgentConfirmationResult(succeeded, null, false));

        var replay = service.replayPersistedEvents("session-1", 0L);

        assertThat(replay.events().getFirst().payload().path("status").asText()).isEqualTo("SUCCEEDED");
        verify(confirmationService).recover(org.mockito.ArgumentMatchers.eq("action-1"),
                org.mockito.ArgumentMatchers.anyString());
        verifyNoInteractions(submissionService, queryService, sessionCreationService, sessionManagementService,
                runCancellationService);
    }

    @Test
    void shouldReplayDuplicatePendingCardsWithoutConfirmingOrRecovering() {
        AgentMessageSubmissionService submissionService = mock(AgentMessageSubmissionService.class);
        AgentEventReplayService replayService = mock(AgentEventReplayService.class);
        AgentRuntimeQueryService queryService = mock(AgentRuntimeQueryService.class);
        AgentSessionCreationService sessionCreationService = mock(AgentSessionCreationService.class);
        AgentSessionManagementService sessionManagementService = mock(AgentSessionManagementService.class);
        AgentRunCancellationService runCancellationService = mock(AgentRunCancellationService.class);
        AgentConfirmationService confirmationService = mock(AgentConfirmationService.class);
        AgentInteractionRuntimeService service = new AgentInteractionRuntimeService(
                submissionService, replayService, queryService, sessionCreationService, sessionManagementService,
                runCancellationService, confirmationService, new ObjectMapper());
        LocalDateTime time = LocalDateTime.of(2026, 8, 5, 11, 20);
        AgentRuntimeEvent card = new AgentRuntimeEvent(8L, "session-1", "run-1", AgentEventType.CARD,
                new AgentStoredJson("""
                        {"actionId":"action-1","actionType":"CREATE_ORDER","status":"PENDING_CONFIRMATION",
                         "expireAt":"2026-08-05T11:25:00+08:00","displayTitle":"确认建单"}
                        """), time.plusDays(30), time);
        AgentConfirmationAction pending = AgentConfirmationAction.pending(
                1L, "action-1", 9L, 10L, 11L, "run-1", "plan-1", 1, "confirm-order",
                new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "4")), time.plusMinutes(5), time);
        when(replayService.replay("session-1", 0L))
                .thenReturn(new AgentEventReplayService.ReplayResult(List.of(card, card), false, 8L));
        when(confirmationService.refreshForCurrentUser("action-1")).thenReturn(Optional.of(pending));

        var replay = service.replayPersistedEvents("session-1", 0L);

        assertThat(replay.events()).hasSize(2);
        verify(confirmationService, org.mockito.Mockito.times(2)).refreshForCurrentUser("action-1");
        verify(confirmationService, never()).recover(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(confirmationService, never()).confirm(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyString());
    }
}
