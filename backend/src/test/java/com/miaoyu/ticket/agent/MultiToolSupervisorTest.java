package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.model.PlanGenerationResponse;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisor;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorRequest;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorResult;
import com.miaoyu.ticket.agent.application.tool.AgentToolDefinitions;
import com.miaoyu.ticket.agent.application.tool.RankMoviePlanExecutionAdapter;
import com.miaoyu.ticket.agent.application.tool.RankMoviePlanExecutionRequest;
import com.miaoyu.ticket.agent.application.tool.RankMoviePlanExecutionResult;
import com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlanNode;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MultiToolSupervisorTest {
    @Test
    void shouldExecuteReadOnlyNodeAndLeaveWriteNodeAwaitingExistingConfirmation() {
        ToolRegistry registry = new ToolRegistry(List.of(
                AgentToolDefinitions.rankMoviePlan(), AgentToolDefinitions.createOrder()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = Mockito.mock(ModelGateway.class);
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        CandidatePlan plan = candidatePlan();
        when(gateway.generatePlan(any()))
                .thenReturn(new PlanGenerationResponse(plan, validator.validate(plan, context())));
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class)))
                .thenAnswer(invocation -> {
            var request = invocation.getArgument(0,
                    com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionRequest.class);
            var running = stateMachine.startNode(request.state(), request.nodeId());
            ToolResult<FixedRecommendationResult> result = new ToolResult<>(
                    ToolStatus.SUCCESS, null, null, false, false, "CONTINUE", false, null, 1L, null, null);
            return new com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionResult(
                    stateMachine.recordToolResult(running, request.nodeId(), result), result);
        });

        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                gateway, registry, validator, stateMachine, List.of(adapter));
        var result = supervisor.run(new MultiToolSupervisorRequest(
                "request-1", "推荐并建单", context(), "run-1", "trace-1", 3_000L));

        assertThat(result.validation().isValid()).isTrue();
        assertThat(result.toolResults()).hasSize(1);
        assertThat(result.toolResults().getFirst().nodeId()).isEqualTo("rank");
        assertThat(result.awaitingConfirmation()).isTrue();
        assertThat(result.safeNextAction()).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(result.state().nodeState("confirm").status())
                .isEqualTo(com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.WAITING_CONFIRMATION);
        assertThat(result.state().nodeState("write").attemptCount()).isZero();
    }

    @Test
    void shouldRaisePlanVersionAndPreserveCompletedReadOnlyNodeWhenReplanning() {
        ToolRegistry registry = new ToolRegistry(List.of(
                AgentToolDefinitions.rankMoviePlan(), AgentToolDefinitions.createOrder()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = Mockito.mock(ModelGateway.class);
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        CandidatePlan firstPlan = candidatePlan();
        CandidatePlan replacement = new CandidatePlan("plan-2", 2, List.of(
                new CandidatePlanNode("confirm-2", PlanNodeType.CONFIRM_ACTION, null, List.of(), List.of(),
                        FailurePolicy.FAIL)));
        when(gateway.generatePlan(any()))
                .thenReturn(new PlanGenerationResponse(firstPlan, validator.validate(firstPlan, context())))
                .thenReturn(new PlanGenerationResponse(replacement, validator.validate(replacement, context())));
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class)))
                .thenAnswer(invocation -> {
            var request = invocation.getArgument(0,
                    com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionRequest.class);
            var running = stateMachine.startNode(request.state(), request.nodeId());
            ToolResult<FixedRecommendationResult> success = new ToolResult<>(
                    ToolStatus.SUCCESS, null, null, false, false, "CONTINUE", false, null, 1L, null, null);
            return new com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionResult(
                    stateMachine.recordToolResult(running, request.nodeId(), success), success);
        });
        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                gateway, registry, validator, stateMachine, List.of(adapter));
        MultiToolSupervisorRequest request = new MultiToolSupervisorRequest(
                "request-3", "推荐并建单", context(), "run-3", "trace-3", 3_000L);

        var replanned = supervisor.replan(request, supervisor.run(request));

        assertThat(replanned.state().plan().version()).isEqualTo(2);
        assertThat(replanned.state().nodeState("rank").status())
                .isEqualTo(com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.SUCCESS);
        assertThat(replanned.state().nodeState("confirm-2").status())
                .isEqualTo(com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.WAITING_CONFIRMATION);
    }

    @Test
    void shouldRejectUnknownToolBeforeAdapterExecution() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = Mockito.mock(ModelGateway.class);
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        CandidatePlan unknown = new CandidatePlan("unknown", 1, List.of(new CandidatePlanNode(
                "unknown", PlanNodeType.CALL_TOOL, "notRegistered", List.of(), List.of(), FailurePolicy.FAIL)));
        when(gateway.generatePlan(any()))
                .thenReturn(new PlanGenerationResponse(unknown, validator.validate(unknown, context())));

        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                gateway, registry, validator, stateMachine, List.of(adapter));
        var result = supervisor.run(new MultiToolSupervisorRequest(
                "request-2", "测试", context(), "run-2", "trace-2", 3_000L));

        assertThat(result.validation().isValid()).isFalse();
        assertThat(result.safeNextAction()).isEqualTo("PLAN_REJECTED");
        Mockito.verify(adapter, Mockito.never())
                .execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class));
    }

    @Test
    void shouldExecuteEachIndependentReadOnlyNodeOnlyOnce() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = Mockito.mock(ModelGateway.class);
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        CandidatePlan plan = new CandidatePlan("two-read", 1, List.of(
                rankNode("rank-a"), rankNode("rank-b")));
        when(gateway.generatePlan(any()))
                .thenReturn(new PlanGenerationResponse(plan, validator.validate(plan, context())));
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class)))
                .thenAnswer(invocation -> {
                    var request = invocation.getArgument(0,
                            ReadOnlyToolExecutionAdapter.ExecutionRequest.class);
                    var running = stateMachine.startNode(request.state(), request.nodeId());
                    ToolResult<FixedRecommendationResult> success = new ToolResult<>(
                            ToolStatus.SUCCESS, null, null, false, false, "CONTINUE", false, null, 1L, null, null);
                    return new com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionResult(
                            stateMachine.recordToolResult(running, request.nodeId(), success), success);
                });
        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                gateway, registry, validator, stateMachine, List.of(adapter));

        var result = supervisor.run(new MultiToolSupervisorRequest(
                "request-4", "双推荐", context(), "run-4", "trace-4", 3_000L));

        assertThat(result.toolResults()).extracting(MultiToolSupervisorResult.NodeToolResult::nodeId)
                .containsExactlyInAnyOrder("rank-a", "rank-b");
        Mockito.verify(adapter, Mockito.times(2))
                .execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class));
    }

    private static CandidatePlan candidatePlan() {
        return new CandidatePlan("plan-1", 1, List.of(
                new CandidatePlanNode(
                        "rank", PlanNodeType.CALL_TOOL, RankMoviePlanTool.TARGET_NAME,
                        List.of(new InputReference("movieId", InputReferenceSource.SLOT, "movieId"),
                                new InputReference("cinemaId", InputReferenceSource.SLOT, "cinemaId"),
                                new InputReference("date", InputReferenceSource.SLOT, "date")),
                        List.of(), FailurePolicy.FAIL),
                new CandidatePlanNode("confirm", PlanNodeType.CONFIRM_ACTION, null, List.of(), List.of("rank"),
                        FailurePolicy.FAIL),
                new CandidatePlanNode(
                        "write", PlanNodeType.CALL_TOOL, "createOrder",
                        List.of(new InputReference("showId", InputReferenceSource.SLOT, "showId"),
                                new InputReference("seatIds", InputReferenceSource.SLOT, "seatIds")),
                        List.of("confirm"), FailurePolicy.FAIL)));
    }

    private static CandidatePlanNode rankNode(String nodeId) {
        return new CandidatePlanNode(
                nodeId, PlanNodeType.CALL_TOOL, RankMoviePlanTool.TARGET_NAME,
                List.of(new InputReference("movieId", InputReferenceSource.SLOT, "movieId"),
                        new InputReference("cinemaId", InputReferenceSource.SLOT, "cinemaId"),
                        new InputReference("date", InputReferenceSource.SLOT, "date")),
                List.of(), FailurePolicy.FAIL);
    }

    private static PlanValidationContext context() {
        return new PlanValidationContext(
                Map.of("movieId", String.class, "cinemaId", String.class, "date", LocalDate.class,
                        "showId", String.class, "seatIds", List.class),
                Map.of(),
                new SlotSnapshot(1L, Map.of(
                        "movieId", "1", "cinemaId", "2", "date", "2026-08-06", "showId", "3",
                        "seatIds", "4,5")));
    }
}
