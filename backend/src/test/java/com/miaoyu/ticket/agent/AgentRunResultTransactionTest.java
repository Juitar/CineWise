package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.AgentDistanceContextApplicationService;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentPersistenceJsonFactory;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunResultTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.ErrorReplyFacts;
import com.miaoyu.ticket.agent.application.reply.ProgressReplyFacts;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorResult;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentResult;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
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
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.agent.application.tool.AgentToolDefinitions;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlanResult;
import com.miaoyu.ticket.ticketing.api.QueryShowsToolResult;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
    void shouldKeepRunRunningWhenDownstreamNodeIsPending() {
        Fixture fixture = fixture();
        ExecutionPlan plan = new ExecutionPlan("plan-2", 1, List.of(
                new ExecutionPlanNode("running", PlanNodeType.ASK_USER, null, List.of(), List.of(),
                        FailurePolicy.ASK_USER, PlanNodeStatus.PENDING, false, false, null, null,
                        new SlotSnapshot(3L, Map.of())),
                new ExecutionPlanNode("downstream", PlanNodeType.ASK_USER, null, List.of(), List.of("running"),
                        FailurePolicy.ASK_USER, PlanNodeStatus.PENDING, false, false, null, null,
                        new SlotSnapshot(3L, Map.of()))));
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(new ToolRegistry(List.of()));
        var state = stateMachine.startNode(stateMachine.initialize(plan), "running");
        when(fixture.runRepository().updateRunningPlanWithCas(any(), eq(0L))).thenReturn(true);

        AgentRun recorded = fixture.transaction().record(run(), result(plan, state));

        assertEquals(AgentRunStatus.RUNNING, recorded.status());
        verify(fixture.sessionRepository(), never()).releaseActiveRun(any(Long.class), any(Long.class));
    }

    @Test
    void shouldKeepProcessingToolAndProgressReplyStreaming() {
        Fixture fixture = fixture();
        ExecutionPlan plan = new ExecutionPlan("plan-processing", 1, List.of(new ExecutionPlanNode(
                "rank", PlanNodeType.CALL_TOOL, "rankMoviePlan", List.of(), List.of(), FailurePolicy.FAIL,
                PlanNodeStatus.PENDING, false, false, null, null, new SlotSnapshot(3L, Map.of()))));
        ExecutionPlanStateMachine machine = new ExecutionPlanStateMachine(
                new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan())));
        var running = machine.startNode(machine.initialize(plan), "rank");
        ToolResult<RecommendationPlanResult> processing = new ToolResult<>(
                ToolStatus.PROCESSING, null, null, false, false, null, false, null, 1L, null, null);
        MinimalReadOnlyAgentResult result = new MinimalReadOnlyAgentResult(
                new CandidatePlan("plan-processing", 1, List.of()), PlanValidationResult.valid(plan), running,
                List.of(processing), new ReplyGenerationResponse(
                        "推荐节点仍在处理中。", AgentReplyMessageType.PROGRESS, new ProgressReplyFacts("rank")));
        when(fixture.runRepository().updateRunningPlanWithCas(any(), eq(0L))).thenReturn(true);

        AgentRun recorded = fixture.transaction().record(run(), result);

        assertEquals(AgentRunStatus.RUNNING, recorded.status());
        ArgumentCaptor<AgentEventType> eventTypes = ArgumentCaptor.forClass(AgentEventType.class);
        verify(fixture.runtimeEventService(), Mockito.atLeastOnce()).append(any(), any(), eventTypes.capture(), any());
        assertEquals(true, eventTypes.getAllValues().contains(AgentEventType.TOOL_START));
        assertEquals(true, eventTypes.getAllValues().contains(AgentEventType.MESSAGE_START));
        assertEquals(false, eventTypes.getAllValues().contains(AgentEventType.TOOL_COMPLETE));
        assertEquals(false, eventTypes.getAllValues().contains(AgentEventType.MESSAGE_COMPLETE));
        assertEquals(false, eventTypes.getAllValues().contains(AgentEventType.RUN_COMPLETE));
    }

    @Test
    void shouldPersistWaitingConfirmationWithoutExecutionTimestamps() {
        Fixture fixture = fixture();
        ExecutionPlan plan = new ExecutionPlan("plan-confirm", 1, List.of(new ExecutionPlanNode(
                "confirm", PlanNodeType.CONFIRM_ACTION, null, List.of(), List.of(), FailurePolicy.FAIL,
                PlanNodeStatus.PENDING, true, false, null, null, new SlotSnapshot(3L, Map.of()))));
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(new ToolRegistry(List.of()));
        when(fixture.runRepository().updateRunningPlanWithCas(any(), eq(0L))).thenReturn(true);

        AgentRun recorded = fixture.transaction().record(run(), result(plan, stateMachine.initialize(plan)));

        assertEquals(AgentRunStatus.RUNNING, recorded.status());
        ArgumentCaptor<List<AgentRunStep>> steps = ArgumentCaptor.forClass(List.class);
        verify(fixture.stepRepository()).insertAll(steps.capture());
        AgentRunStep step = steps.getValue().getFirst();
        assertEquals(PlanNodeStatus.WAITING_CONFIRMATION, step.status());
        assertEquals(0, step.attemptCount());
        assertEquals(0, step.retryCount());
        assertEquals(null, step.startedAt());
        assertEquals(null, step.finishedAt());
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

    @Test
    void shouldPersistToolCompleteAndToolErrorWithStableNodeId() {
        Fixture fixture = fixture();
        ExecutionPlan plan = new ExecutionPlan("plan-tool", 1, List.of(new ExecutionPlanNode(
                "rank", PlanNodeType.CALL_TOOL, "rankMoviePlan", List.of(), List.of(), FailurePolicy.FAIL,
                PlanNodeStatus.PENDING, false, false, null, null, new SlotSnapshot(3L, Map.of()))));
        ExecutionPlanStateMachine machine = new ExecutionPlanStateMachine(
                new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan())));
        var running = machine.startNode(machine.initialize(plan), "rank");
        ToolResult<RecommendationPlanResult> success = new ToolResult<>(
                ToolStatus.SUCCESS,
                new RecommendationPlanResult("1.0", "v1", List.of(), List.of("SHOWTIME"), null, false, "fixture",
                        Instant.parse("2026-08-04T00:00:00Z"), Instant.parse("2026-08-04T01:00:00Z"), true),
                null, false, false, null, false, null, 1L, null, null);
        var completed = machine.recordToolResult(running, "rank", success);
        when(fixture.runRepository().updateTerminalWithCas(any(), eq(0L))).thenReturn(true);

        fixture.transaction().record(run(), new MultiToolSupervisorResult(
                new CandidatePlan("plan-tool", 1, List.of()), PlanValidationResult.valid(plan), completed,
                List.of(new MultiToolSupervisorResult.NodeToolResult("rank", success)), false, null));

        ArgumentCaptor<AgentStoredJson> completePayload = ArgumentCaptor.forClass(AgentStoredJson.class);
        verify(fixture.runtimeEventService()).append(any(), any(), eq(AgentEventType.TOOL_COMPLETE),
                completePayload.capture());
        String expectedCompletePayload = "{\"nodeId\":\"rank\",\"toolName\":\"rankMoviePlan\","
                + "\"displayText\":\"正在整理推荐方案\",\"degraded\":false}";
        assertEquals(expectedCompletePayload, completePayload.getValue().value());

        ToolResult<RecommendationPlanResult> failure = new ToolResult<>(
                ToolStatus.FAILED, null, 306002, false, false, "CHECK_INPUT", false, null, 1L, null, null);
        var failedState = machine.recordToolResult(
                machine.startNode(machine.initialize(plan), "rank"), "rank", failure);
        when(fixture.runRepository().updateTerminalWithCas(any(), eq(0L))).thenReturn(true);
        fixture.transaction().record(run(), new MultiToolSupervisorResult(
                new CandidatePlan("plan-tool", 1, List.of()), PlanValidationResult.valid(plan), failedState,
                List.of(new MultiToolSupervisorResult.NodeToolResult("rank", failure)), false, null));
        ArgumentCaptor<AgentStoredJson> errorPayload = ArgumentCaptor.forClass(AgentStoredJson.class);
        verify(fixture.runtimeEventService()).append(any(), any(), eq(AgentEventType.TOOL_ERROR),
                errorPayload.capture());
        String expectedErrorPayload = "{\"nodeId\":\"rank\",\"toolName\":\"rankMoviePlan\","
                + "\"displayText\":\"工具暂时无法完成查询\",\"errorCode\":306002,"
                + "\"retryable\":false,\"replanSuggested\":false}";
        assertEquals(expectedErrorPayload, errorPayload.getValue().value());
        ArgumentCaptor<AgentMessage> messages = ArgumentCaptor.forClass(AgentMessage.class);
        verify(fixture.messageRepository(), Mockito.times(2)).insert(messages.capture());
        assertEquals(AgentMessageType.PLAN_CARD, messages.getAllValues().getFirst().type());
        assertEquals(AgentMessageType.ERROR, messages.getAllValues().getLast().type());
    }

    @Test
    void shouldPersistSelectSeatsCardFromFreshQueryShowsResult() throws Exception {
        Fixture fixture = fixture();
        ExecutionPlan plan = new ExecutionPlan("plan-shows", 1, List.of(new ExecutionPlanNode(
                "shows", PlanNodeType.CALL_TOOL, "queryShows", List.of(), List.of(), FailurePolicy.FAIL,
                PlanNodeStatus.PENDING, false, false, null, null, new SlotSnapshot(3L, Map.of()))));
        ExecutionPlanStateMachine machine = new ExecutionPlanStateMachine(
                new ToolRegistry(List.of(AgentToolDefinitions.queryShows())));
        Instant dataAt = Instant.parse("2026-08-04T02:00:00Z");
        ToolResult<QueryShowsToolResult> success = new ToolResult<>(
                ToolStatus.SUCCESS,
                new QueryShowsToolResult(List.of(new QueryShowsToolResult.ShowItem(
                        "70001", "101", "201", "测试影院", "301", "1号厅",
                        OffsetDateTime.parse("2026-08-04T04:00:00+00:00"),
                        OffsetDateTime.parse("2026-08-04T06:00:00+00:00"),
                        OffsetDateTime.parse("2026-08-04T03:00:00+00:00"),
                        "国语2D", "50.00", 20, "SCHEDULED", "NORMAL", 1,
                        OffsetDateTime.parse("2026-08-04T02:00:00+00:00")))),
                null, false, false, "VIEW_SHOWS", false, null, 3L, dataAt, dataAt.plusSeconds(60));
        var running = machine.startNode(machine.initialize(plan), "shows");
        var completed = machine.recordToolResult(running, "shows", success);
        when(fixture.runRepository().updateTerminalWithCas(any(), eq(0L))).thenReturn(true);

        fixture.transaction().record(run(), new MultiToolSupervisorResult(
                new CandidatePlan("plan-shows", 1, List.of()), PlanValidationResult.valid(plan), completed,
                List.of(new MultiToolSupervisorResult.NodeToolResult("shows", "queryShows", success)), false, null));

        ArgumentCaptor<AgentMessage> message = ArgumentCaptor.forClass(AgentMessage.class);
        verify(fixture.messageRepository()).insert(message.capture());
        assertEquals(AgentMessageType.SELECT_SEATS, message.getValue().type());
        ArgumentCaptor<AgentEventType> eventType = ArgumentCaptor.forClass(AgentEventType.class);
        ArgumentCaptor<AgentStoredJson> eventPayload = ArgumentCaptor.forClass(AgentStoredJson.class);
        verify(fixture.runtimeEventService(), Mockito.atLeastOnce()).append(
                any(), any(), eventType.capture(), eventPayload.capture());
        int cardIndex = eventType.getAllValues().indexOf(AgentEventType.CARD);
        assertEquals(true, cardIndex >= 0);
        var payload = new ObjectMapper().readTree(eventPayload.getAllValues().get(cardIndex).value());
        assertEquals("BUSINESS_INTENT", payload.path("type").asText());
        assertEquals("SELECT_SEATS", payload.path("payload").path("intent").asText());
        assertEquals("70001", payload.path("payload").path("businessRef").path("showId").asText());
        assertEquals("101", payload.path("payload").path("businessRef").path("movieId").asText());
        assertEquals("201", payload.path("payload").path("businessRef").path("cinemaId").asText());
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
        AgentRuntimeEventService runtimeEventService = Mockito.mock(AgentRuntimeEventService.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 4, 10, 0);
        when(sessionRepository.findByIdAndUserId(1L, 7L)).thenReturn(java.util.Optional.of(new AgentSession(
                1L, "session-1", 7L, null, AgentSessionStatus.ACTIVE, 100L, 0L,
                now, now, now.plusDays(30))));
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
                new AgentPersistenceJsonFactory(new ObjectMapper().findAndRegisterModules()),
                runtimeEventService,
                idGenerator,
                Clock.fixed(Instant.parse("2026-08-04T02:00:00Z"), ZoneId.of("Asia/Shanghai")),
                Mockito.mock(AgentDistanceContextApplicationService.class));
        return new Fixture(transaction, runRepository, stepRepository, messageRepository, sessionRepository,
                runtimeEventService);
    }

    private record Fixture(
            AgentRunResultTransaction transaction,
            AgentRunRepository runRepository,
            AgentRunStepRepository stepRepository,
            AgentMessageRepository messageRepository,
            AgentSessionRepository sessionRepository,
            AgentRuntimeEventService runtimeEventService) {
    }
}
