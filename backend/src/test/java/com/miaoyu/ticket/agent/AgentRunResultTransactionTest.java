package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentPersistenceJsonFactory;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunResultTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.ErrorReplyFacts;
import com.miaoyu.ticket.agent.application.reply.ProgressReplyFacts;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentResult;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlan;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationIssue;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationIssueCode;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationResult;
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

/** 结果短事务只保存安全计划快照，并在终态 CAS 成功后才释放会话。 */
class AgentRunResultTransactionTest {

    @Test
    void shouldKeepRunningForProcessingAndStoreValidatedStepJson() {
        Fixture fixture = fixture();
        ExecutionPlan plan = plan();
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(new ToolRegistry(List.of()));
        var state = stateMachine.startNode(stateMachine.initialize(plan), "ask-date");
        when(fixture.runRepository().updateRunningPlanWithCas(any(), eq(0L))).thenReturn(true);

        AgentRun recorded = fixture.transaction().record(run(), result(plan, state));

        assertEquals(AgentRunStatus.RUNNING, recorded.status());
        ArgumentCaptor<List<AgentRunStep>> steps = ArgumentCaptor.forClass(List.class);
        verify(fixture.stepRepository()).insertAll(steps.capture());
        assertEquals(1, steps.getValue().size());
        AgentRunStep step = steps.getValue().getFirst();
        assertEquals(PlanNodeStatus.RUNNING, step.status());
        assertEquals(true, step.recoveryPending());
        assertEquals("[]", step.dependsOn().value());
        assertEquals("[]", step.inputRefs().value());
        assertEquals("{\"version\":3,\"values\":{}}", step.slotSnapshot().value());
        verify(fixture.sessionRepository(), never()).releaseActiveRun(any(Long.class), any(Long.class));
    }

    @Test
    void shouldReleaseSessionOnlyAfterTerminalCasSucceeds() {
        Fixture fixture = fixture();
        when(fixture.runRepository().updateTerminalWithCas(any(), eq(0L))).thenReturn(false);

        fixture.transaction().recordFailure(run());

        verify(fixture.sessionRepository(), never()).releaseActiveRun(any(Long.class), any(Long.class));
        verify(fixture.messageRepository(), never()).insert(any());
    }

    @Test
    void shouldStoreSafeErrorAndReleaseSessionAfterTerminalCasSucceeds() {
        Fixture fixture = fixture();
        when(fixture.runRepository().updateTerminalWithCas(any(), eq(0L))).thenReturn(true);

        fixture.transaction().recordFailure(run());

        ArgumentCaptor<AgentMessage> message = ArgumentCaptor.forClass(AgentMessage.class);
        verify(fixture.messageRepository()).insert(message.capture());
        assertEquals(AgentReplyMessageType.ERROR.name(), message.getValue().type().name());
        assertEquals("{\"reason\":\"RUN_FAILED\"}", message.getValue().payload().value());
        verify(fixture.sessionRepository()).releaseActiveRun(1L, 100L);
    }

    @Test
    void shouldPersistInvalidPlanAsFailedWithoutCandidatePlanOrToolResult() {
        Fixture fixture = fixture();
        when(fixture.runRepository().updateTerminalWithCas(any(), eq(0L))).thenReturn(true);
        MinimalReadOnlyAgentResult invalidResult = new MinimalReadOnlyAgentResult(
                new CandidatePlan("candidate-1", 1, List.of()),
                PlanValidationResult.invalid(List.of(new PlanValidationIssue(
                        PlanValidationIssueCode.TOOL_NOT_FOUND, "rank", "targetName", "ignored"))),
                null,
                List.of(),
                new ReplyGenerationResponse(
                        "当前请求无法安全执行", AgentReplyMessageType.ERROR,
                        new ErrorReplyFacts(null, List.of("TOOL_NOT_FOUND"))));

        AgentRun recorded = fixture.transaction().record(run(), invalidResult);

        assertEquals(AgentRunStatus.FAILED, recorded.status());
        verify(fixture.stepRepository(), never()).insertAll(any());
        verify(fixture.sessionRepository()).releaseActiveRun(1L, 100L);
    }

    private static MinimalReadOnlyAgentResult result(
            ExecutionPlan plan, com.miaoyu.ticket.agent.domain.run.ExecutionRunState state) {
        return new MinimalReadOnlyAgentResult(
                new CandidatePlan(plan.planId(), plan.version(), List.of()),
                PlanValidationResult.valid(plan),
                state,
                List.of(),
                new ReplyGenerationResponse(
                        "正在查询", AgentReplyMessageType.PROGRESS, new ProgressReplyFacts("ask-date")));
    }

    private static ExecutionPlan plan() {
        return new ExecutionPlan(
                "plan-1", 1,
                List.of(new ExecutionPlanNode(
                        "ask-date", PlanNodeType.ASK_USER, null, List.of(), List.of(), FailurePolicy.ASK_USER,
                        PlanNodeStatus.PENDING, false, false, null, null, new SlotSnapshot(3L, Map.of()))));
    }

    private static AgentRun run() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 4, 10, 0);
        return new AgentRun(
                100L, "run-1", 1L, 7L, "request-1", new AgentRequestHash("v1", "a".repeat(64)),
                null, null, AgentRunStatus.RUNNING, "trace", now, null, 0L, now, now, now.plusDays(30));
    }

    private static Fixture fixture() {
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        AgentRunStepRepository stepRepository = Mockito.mock(AgentRunStepRepository.class);
        AgentMessageRepository messageRepository = Mockito.mock(AgentMessageRepository.class);
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        BusinessIdGenerator idGenerator = new BusinessIdGenerator() {
            private long next = 1000L;

            @Override
            public long nextId() {
                return next++;
            }
        };
        AgentRunResultTransaction transaction = new AgentRunResultTransaction(
                runRepository,
                stepRepository,
                messageRepository,
                sessionRepository,
                new AgentPersistenceJsonFactory(new ObjectMapper()),
                idGenerator,
                Clock.fixed(Instant.parse("2026-08-04T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
        return new Fixture(transaction, runRepository, stepRepository, messageRepository, sessionRepository);
    }

    private record Fixture(
            AgentRunResultTransaction transaction,
            AgentRunRepository runRepository,
            AgentRunStepRepository stepRepository,
            AgentMessageRepository messageRepository,
            AgentSessionRepository sessionRepository) {
    }
}
