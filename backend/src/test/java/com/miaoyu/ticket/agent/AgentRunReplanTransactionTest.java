package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.persistence.AgentPersistenceJsonFactory;
import com.miaoyu.ticket.agent.application.persistence.AgentRunReplanTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlan;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AgentRunReplanTransactionTest {
    @Test
    void shouldSaveHigherPlanVersionBeforePublishingNewPlanEvent() {
        Fixture fixture = fixture(true);

        AgentRun next = fixture.transaction().record(run(), replannedState());

        assertEquals(2, next.planVersion());
        verify(fixture.runRepository()).updateRunningPlanWithCas(any(), eq(0L));
        ArgumentCaptor<List<com.miaoyu.ticket.agent.domain.persistence.AgentRunStep>> steps =
                ArgumentCaptor.forClass(List.class);
        verify(fixture.stepRepository()).insertAll(steps.capture());
        assertEquals(1, steps.getValue().size());
        assertEquals("confirm-2", steps.getValue().getFirst().nodeId());
        assertEquals(PlanNodeStatus.WAITING_CONFIRMATION, steps.getValue().getFirst().status());
        verify(fixture.runtimeEventService()).append(any(), eq(next), any(), any());
    }

    @Test
    void shouldNotInsertStepsOrPublishEventAfterCasConflict() {
        Fixture fixture = fixture(false);

        assertThrows(IllegalStateException.class, () -> fixture.transaction().record(run(), replannedState()));

        verify(fixture.stepRepository(), never()).findByRunId(any(Long.class));
        verify(fixture.stepRepository(), never()).insertAll(any());
        verify(fixture.runtimeEventService(), never()).append(any(), any(), any(), any());
    }

    private static com.miaoyu.ticket.agent.domain.run.ExecutionRunState replannedState() {
        ExecutionPlan initial = new ExecutionPlan("plan-1", 1, List.of(new ExecutionPlanNode(
                "ask", PlanNodeType.ASK_USER, null, List.of(), List.of(), FailurePolicy.ASK_USER,
                PlanNodeStatus.PENDING, false, false, null, null, new SlotSnapshot(1L, Map.of()))));
        ExecutionPlan replacement = new ExecutionPlan("plan-2", 2, List.of(new ExecutionPlanNode(
                "confirm-2", PlanNodeType.CONFIRM_ACTION, null, List.of(), List.of(), FailurePolicy.FAIL,
                PlanNodeStatus.PENDING, true, false, null, null, new SlotSnapshot(1L, Map.of()))));
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(new ToolRegistry(List.of()));
        return stateMachine.acceptReplan(stateMachine.initialize(initial), replacement);
    }

    private static AgentRun run() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 15, 0);
        return new AgentRun(
                100L, "run-1", 1L, 7L, "request-1",
                new com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash("v1", "a".repeat(64)),
                "plan-1", 1, AgentRunStatus.RUNNING, "trace", now, null, 0L, now, now, now.plusDays(30));
    }

    private static Fixture fixture(boolean casResult) {
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        AgentRunStepRepository stepRepository = Mockito.mock(AgentRunStepRepository.class);
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        AgentRuntimeEventService runtimeEventService = Mockito.mock(AgentRuntimeEventService.class);
        when(runRepository.updateRunningPlanWithCas(any(), eq(0L))).thenReturn(casResult);
        when(stepRepository.findByRunId(100L)).thenReturn(List.of());
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 15, 0);
        when(sessionRepository.findByIdAndUserId(1L, 7L)).thenReturn(java.util.Optional.of(new AgentSession(
                1L, "session-1", 7L, null, AgentSessionStatus.ACTIVE, 100L, 0L, now, now, now.plusDays(30))));
        BusinessIdGenerator ids = new BusinessIdGenerator() {
            @Override
            public long nextId() {
                return 1000L;
            }
        };
        return new Fixture(new AgentRunReplanTransaction(
                runRepository, stepRepository, sessionRepository, new AgentPersistenceJsonFactory(new ObjectMapper()),
                runtimeEventService, ids,
                Clock.fixed(Instant.parse("2026-08-06T07:00:00Z"), ZoneId.of("Asia/Shanghai"))),
                runRepository, stepRepository, runtimeEventService);
    }

    private record Fixture(
            AgentRunReplanTransaction transaction,
            AgentRunRepository runRepository,
            AgentRunStepRepository stepRepository,
            AgentRuntimeEventService runtimeEventService) {
    }
}
