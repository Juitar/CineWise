package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationActionCreationService;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderConfirmationActionOrchestrator;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderConfirmationCommandFactory;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CreateOrderConfirmationActionOrchestratorTest {
    @Test
    void shouldRejectWhenConfirmationStepWasNotPersisted() {
        AgentRunStepRepository steps = Mockito.mock(AgentRunStepRepository.class);
        when(steps.findByRunId(11L)).thenReturn(List.of());
        var factory = Mockito.mock(CreateOrderConfirmationCommandFactory.class);
        var actions = Mockito.mock(AgentConfirmationActionCreationService.class);
        var orchestrator = new CreateOrderConfirmationActionOrchestrator(steps, factory, actions);

        assertThatThrownBy(() -> orchestrator.create(run(), confirmation(), write()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未持久化");
        Mockito.verifyNoInteractions(factory, actions);
    }

    @Test
    void shouldCreateExistingActionOnlyAfterMatchingPersistedConfirmationStep() {
        AgentRunStepRepository steps = Mockito.mock(AgentRunStepRepository.class);
        when(steps.findByRunId(11L)).thenReturn(List.of(waitingStep()));
        var factory = Mockito.mock(CreateOrderConfirmationCommandFactory.class);
        when(factory.create(write())).thenReturn(new ConfirmedOrderCommand("createOrder", "70001", List.of("2")));
        var actions = Mockito.mock(AgentConfirmationActionCreationService.class);
        var orchestrator = new CreateOrderConfirmationActionOrchestrator(steps, factory, actions);

        orchestrator.create(run(), confirmation(), write());

        verify(actions).create(any());
    }

    private static AgentRun run() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 10, 0);
        return new AgentRun(11L, "run-1", 10L, 9L, "request-1", new AgentRequestHash("v1", "a".repeat(64)),
                "plan-1", 2, AgentRunStatus.RUNNING, "trace", now, null, 0L, now, now, now.plusDays(1));
    }

    private static ExecutionPlanNode confirmation() {
        return new ExecutionPlanNode("confirm", PlanNodeType.CONFIRM_ACTION, null, List.of(), List.of(),
                FailurePolicy.FAIL, PlanNodeStatus.PENDING, true, false, null, null, new SlotSnapshot(1L, Map.of()));
    }

    private static ExecutionPlanNode write() {
        return new ExecutionPlanNode("write", PlanNodeType.CALL_TOOL, "createOrder", List.of(), List.of("confirm"),
                FailurePolicy.FAIL, PlanNodeStatus.PENDING, true, false, null, null, new SlotSnapshot(1L, Map.of()));
    }

    private static AgentRunStep waitingStep() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 10, 0);
        return new AgentRunStep(12L, 11L, 2, "confirm", PlanNodeType.CONFIRM_ACTION,
                new AgentStoredJson("[]"), new AgentStoredJson("[]"), PlanNodeStatus.WAITING_CONFIRMATION,
                FailurePolicy.FAIL, 0, 0, false, false, null, null, new AgentStoredJson("{}"), null, null,
                0L, now, now, now.plusDays(1));
    }
}
