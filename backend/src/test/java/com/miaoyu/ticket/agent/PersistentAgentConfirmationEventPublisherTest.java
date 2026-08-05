package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.infrastructure.confirmation.PersistentAgentConfirmationEventPublisher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 确认卡只能是已保存 action 的受控投影，不能包含建单 Command 或稳定写标识。 */
class PersistentAgentConfirmationEventPublisherTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 10, 0);

    private AgentSessionRepository sessionRepository;
    private AgentRunRepository runRepository;
    private AgentRuntimeEventService runtimeEventService;
    private PersistentAgentConfirmationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        sessionRepository = Mockito.mock(AgentSessionRepository.class);
        runRepository = Mockito.mock(AgentRunRepository.class);
        runtimeEventService = Mockito.mock(AgentRuntimeEventService.class);
        publisher = new PersistentAgentConfirmationEventPublisher(sessionRepository, runRepository,
                runtimeEventService, new ObjectMapper());
    }

    @Test
    void shouldPublishOnlySafeCardForTheMatchingPersistedRun() throws Exception {
        AgentConfirmationAction action = action();
        when(runRepository.findByRunIdAndUserId("run-1", 9L)).thenReturn(Optional.of(run()));
        when(sessionRepository.findByIdAndUserId(10L, 9L)).thenReturn(Optional.of(session()));
        ArgumentCaptor<AgentStoredJson> payload = ArgumentCaptor.forClass(AgentStoredJson.class);

        publisher.publish(action);

        verify(runtimeEventService).append(Mockito.eq(session()), Mockito.eq(run()),
                Mockito.eq(AgentEventType.CARD), payload.capture());
        var json = new ObjectMapper().readTree(payload.getValue().value());
        assertThat(json.path("actionId").asText()).isEqualTo("action-1");
        assertThat(json.path("actionType").asText()).isEqualTo("CREATE_ORDER");
        assertThat(json.path("status").asText()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(json.path("expireAt").asText()).contains("+08:00");
        assertThat(json.path("displayLines").isArray()).isTrue();
        assertThat(json.has("planVersion")).isFalse();
        assertThat(json.has("showId")).isFalse();
        assertThat(json.has("seatIds")).isFalse();
        assertThat(json.has("parameterHash")).isFalse();
        assertThat(json.has("idempotencyKey")).isFalse();
    }

    @Test
    void shouldNotPublishWhenTheActionRunOrSessionCannotBeProved() {
        when(runRepository.findByRunIdAndUserId("run-1", 9L)).thenReturn(Optional.of(runWithId(12L)));

        publisher.publish(action());

        verify(runtimeEventService, never()).append(any(), any(), any(), any());
    }

    private static AgentConfirmationAction action() {
        return AgentConfirmationAction.pending(1L, "action-1", 9L, 10L, 11L, "run-1", "plan-1", 2,
                "confirm-order", new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "4")),
                NOW.plusMinutes(5), NOW);
    }

    private static AgentRun run() {
        return runWithId(11L);
    }

    private static AgentRun runWithId(long id) {
        return new AgentRun(id, "run-1", 10L, 9L, "request-1", new AgentRequestHash("v1", "a".repeat(64)),
                "plan-1", 2, AgentRunStatus.RUNNING, "trace-1", NOW, null, 0L, NOW, NOW, NOW.plusDays(30));
    }

    private static AgentSession session() {
        return new AgentSession(10L, "session-1", 9L, null, AgentSessionStatus.ACTIVE, 11L,
                0L, NOW, NOW, NOW.plusDays(30));
    }
}
