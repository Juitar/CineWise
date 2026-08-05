package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.infrastructure.confirmation.PersistentAgentConfirmationFactsProvider;
import com.miaoyu.ticket.agent.infrastructure.confirmation.PersistentAgentActionAuthorizationFactsProvider;
import com.miaoyu.ticket.order.api.CreateOrderTool;
import com.miaoyu.ticket.order.api.OrderPrecheckResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 确认前事实只读取 B 的运行记录和 A 的只读预检，不能把非本人请求送往 A。 */
class PersistentAgentConfirmationFactsProviderTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 10, 0);

    private AgentRunRepository runRepository;
    private CreateOrderTool createOrderTool;
    private PersistentAgentConfirmationFactsProvider provider;

    @BeforeEach
    void setUp() {
        runRepository = Mockito.mock(AgentRunRepository.class);
        createOrderTool = Mockito.mock(CreateOrderTool.class);
        provider = new PersistentAgentConfirmationFactsProvider(runRepository, createOrderTool,
                Clock.fixed(Instant.parse("2026-08-05T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void shouldNotCallPrecheckForAnotherUserOrEndedRun() {
        var foreign = provider.load(action(), 10L);
        assertThat(foreign.businessDataValid()).isFalse();
        verify(createOrderTool, never()).validate(any());

        when(runRepository.findByRunIdAndUserId("run-1", 9L))
                .thenReturn(Optional.of(run(AgentRunStatus.COMPLETED, "plan-1", 2)));
        var ended = provider.load(action(), 9L);
        assertThat(ended.businessDataValid()).isFalse();
        assertThat(ended.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        verify(createOrderTool, never()).validate(any());
    }

    @Test
    void shouldExposeCurrentPlanAndPrecheckFailureWithoutCallingWriteTool() {
        when(runRepository.findByRunIdAndUserId("run-1", 9L))
                .thenReturn(Optional.of(run(AgentRunStatus.RUNNING, "plan-2", 3)));
        when(createOrderTool.validate(any())).thenReturn(OrderPrecheckResult.rejected(204001));

        var facts = provider.load(action(), 9L);

        assertThat(facts.planId()).isEqualTo("plan-2");
        assertThat(facts.planVersion()).isEqualTo(3);
        assertThat(facts.businessDataValid()).isFalse();
        verify(createOrderTool).validate(any());
    }

    @Test
    void shouldPermitOnlyRunningOwnerWithSuccessfulReadOnlyPrecheck() {
        when(runRepository.findByRunIdAndUserId("run-1", 9L))
                .thenReturn(Optional.of(run(AgentRunStatus.RUNNING, "plan-1", 2)));
        when(createOrderTool.validate(any())).thenReturn(OrderPrecheckResult.allowed());

        var facts = provider.load(action(), 9L);

        assertThat(facts.runStatus()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(facts.planId()).isEqualTo("plan-1");
        assertThat(facts.planVersion()).isEqualTo(2);
        assertThat(facts.businessDataValid()).isTrue();
        verify(createOrderTool).validate(any());
    }

    @Test
    void shouldExposeOnlyRunningUnexpiredFactsToThePublicAuthorizationPort() {
        when(runRepository.findByRunIdAndUserId("run-1", 9L))
                .thenReturn(Optional.of(run(AgentRunStatus.RUNNING, "plan-1", 2)));
        PersistentAgentActionAuthorizationFactsProvider authorizationFacts =
                new PersistentAgentActionAuthorizationFactsProvider(runRepository,
                        Clock.fixed(Instant.parse("2026-08-05T02:00:00Z"), ZoneId.of("Asia/Shanghai")));

        var facts = authorizationFacts.load(action());

        assertThat(facts.executable()).isTrue();
        assertThat(facts.runId()).isEqualTo("run-1");
        assertThat(facts.planVersion()).isEqualTo(2);
        assertThat(facts.parameterHash()).isEqualTo(action().parameterHash());
    }

    private static AgentConfirmationAction action() {
        return AgentConfirmationAction.pending(1L, "action-1", 9L, 10L, 11L, "run-1", "plan-1", 2,
                "confirm-order", new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "4")),
                NOW.plusMinutes(5), NOW);
    }

    private static AgentRun run(AgentRunStatus status, String planId, int planVersion) {
        return new AgentRun(11L, "run-1", 10L, 9L, "request-1", new AgentRequestHash("v1", "a".repeat(64)),
                planId, planVersion, status, "trace-1", NOW, status.isTerminal() ? NOW.plusSeconds(1) : null,
                0L, NOW, NOW, NOW.plusHours(1));
    }
}
