package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.tool.AgentToolDefinitions;
import com.miaoyu.ticket.agent.application.tool.RankMoviePlanExecutionAdapter;
import com.miaoyu.ticket.agent.application.tool.RankMoviePlanExecutionRequest;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlanNode;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlan;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanCommand;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationCatalog;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationQueryService;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** B 到 D 的推荐工具调用测试，不启动 Controller、SSE 或持久化设施。 */
class RankMoviePlanExecutionAdapterTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void shouldRegisterAndValidateRankMoviePlanInputDefinitions() {
        ToolRegistry registry = registry();
        var definition = registry.find(RankMoviePlanTool.TARGET_NAME).orElseThrow();

        assertThat(definition.commandType()).isEqualTo(RankMoviePlanCommand.class);
        assertThat(definition.resultType()).isEqualTo(FixedRecommendationResult.class);
        assertThat(definition.readOnly()).isTrue();
        assertThat(definition.timeout()).isEqualTo(AgentToolDefinitions.RANK_MOVIE_PLAN_TIMEOUT);
        assertThat(definition.inputs()).extracting("name").containsExactly(
                "movieId", "cinemaId", "date", "timeFrom", "timeTo");

        var validation = new PlanSchemaValidator(registry).validate(
                candidatePlan(FailurePolicy.FAIL, false), validValidationContext());
        assertThat(validation.isValid()).isTrue();

        var invalidValidation = new PlanSchemaValidator(registry).validate(
                candidatePlan(List.of(
                        new InputReference("movieId", InputReferenceSource.SLOT, "movieId"),
                        new InputReference("cinemaId", InputReferenceSource.SLOT, "cinemaId"),
                        new InputReference("date", InputReferenceSource.SLOT, "movieId"),
                        new InputReference("unknown", InputReferenceSource.SLOT, "date"))),
                validValidationContext());
        assertThat(invalidValidation.isValid()).isFalse();
    }

    @Test
    void shouldCallRealDToolAndKeepShowtimeUnavailableAsSuccessfulDegradation() {
        RankMoviePlanTool tool = spy(realUnavailableShowtimeTool());
        ToolRegistry registry = registry();
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ExecutionPlan plan = validatedPlan(registry, FailurePolicy.FAIL, false);
        RankMoviePlanExecutionAdapter adapter = new RankMoviePlanExecutionAdapter(tool, stateMachine);

        var result = adapter.execute(request(stateMachine.initialize(plan)));

        assertThat(result.toolResult().status()).isEqualTo(ToolStatus.SUCCESS);
        assertThat(result.toolResult().data().purchaseEligible()).isFalse();
        assertThat(result.toolResult().data().missingFactors()).containsExactly("SHOWTIME");
        assertThat(result.toolResult().degraded()).isTrue();
        assertThat(result.state().nodeState("rank").status()).isEqualTo(PlanNodeStatus.SUCCESS);
        ArgumentCaptor<ToolContext> contextCaptor = ArgumentCaptor.forClass(ToolContext.class);
        ArgumentCaptor<RankMoviePlanCommand> commandCaptor = ArgumentCaptor.forClass(RankMoviePlanCommand.class);
        verify(tool).execute(contextCaptor.capture(), commandCaptor.capture());
        assertThat(contextCaptor.getValue().runId()).isEqualTo("run-1");
        assertThat(contextCaptor.getValue().nodeId()).isEqualTo("rank");
        assertThat(contextCaptor.getValue().targetName()).isEqualTo(RankMoviePlanTool.TARGET_NAME);
        assertThat(contextCaptor.getValue().inputRefs())
                .containsExactly("slots.movieId", "slots.cinemaId", "slots.date");
        assertThat(contextCaptor.getValue().deadlineMs()).isEqualTo(3_000L);
        assertThat(contextCaptor.getValue().stateVersion()).isEqualTo(1L);
        assertThat(commandCaptor.getValue()).isEqualTo(new RankMoviePlanCommand(
                "101", "201", LocalDate.of(2026, 8, 4), null, null));
    }

    @Test
    void shouldNotCallDWhenSlotsCannotBuildCommandAndSkipDownstream() {
        assertInvalidParameter(
                false, Map.of("movieId", "101", "cinemaId", "201", "date", "not-a-date"));
        assertInvalidParameter(false, Map.of("movieId", "0", "cinemaId", "201", "date", "2026-08-04"));
        assertInvalidParameter(
                true,
                Map.of(
                        "movieId", "101",
                        "cinemaId", "201",
                        "date", "2026-08-04",
                        "timeFrom", "not-a-time",
                        "timeTo", "20:00"));
        assertInvalidParameter(
                true,
                Map.of(
                        "movieId", "101",
                        "cinemaId", "201",
                        "date", "2026-08-04",
                        "timeFrom", "20:00",
                        "timeTo", "18:00"));
        assertInvalidParameter(
                List.of(
                        new InputReference("movieId", InputReferenceSource.SLOT, "movieId"),
                        new InputReference("cinemaId", InputReferenceSource.SLOT, "cinemaId"),
                        new InputReference("date", InputReferenceSource.SLOT, "date"),
                        new InputReference("timeFrom", InputReferenceSource.SLOT, "timeFrom")),
                Map.of("movieId", "101", "cinemaId", "201", "date", "2026-08-04", "timeFrom", "18:00"));
    }

    @Test
    void shouldRetryOnlyOnceForDToolFailure() {
        RankMoviePlanTool tool = mock(RankMoviePlanTool.class);
        ToolRegistry registry = registry();
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        RankMoviePlanExecutionAdapter adapter = new RankMoviePlanExecutionAdapter(tool, stateMachine);
        ToolResult<FixedRecommendationResult> failureResult = new ToolResult<>(
                ToolStatus.FAILED, null, 100001, true, false, "RETRY", false, null, 1L, null, null);
        when(tool.execute(any(ToolContext.class), any(RankMoviePlanCommand.class))).thenReturn(failureResult);

        var first = adapter.execute(request(
                stateMachine.initialize(validatedPlan(registry, FailurePolicy.RETRY_ONCE, false))));
        var second = adapter.execute(request(first.state()));

        assertThat(first.state().nodeState("rank").status()).isEqualTo(PlanNodeStatus.PENDING);
        assertThat(first.state().nodeState("rank").retryCount()).isEqualTo(1);
        assertThat(second.state().nodeState("rank").status()).isEqualTo(PlanNodeStatus.FAILED);
        assertThat(second.state().nodeState("render").status()).isEqualTo(PlanNodeStatus.SKIPPED);
        verify(tool, org.mockito.Mockito.times(2)).execute(any(ToolContext.class), any(RankMoviePlanCommand.class));
    }

    @Test
    void shouldRejectNodeThatIsNotRunnableRankMoviePlanWithoutCallingD() {
        RankMoviePlanTool tool = mock(RankMoviePlanTool.class);
        ToolRegistry registry = registry();
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ExecutionPlan plan = validatedPlan(registry, FailurePolicy.FAIL, false);
        RankMoviePlanExecutionAdapter adapter = new RankMoviePlanExecutionAdapter(tool, stateMachine);

        assertThrows(IllegalStateException.class, () -> adapter.execute(new RankMoviePlanExecutionRequest(
                stateMachine.initialize(plan), "render", "run-1", "trace-1", 3_000L)));

        verify(tool, never()).execute(any(), any());
    }

    private static RankMoviePlanExecutionRequest request(ExecutionRunState state) {
        return new RankMoviePlanExecutionRequest(state, "rank", "run-1", "trace-1", 9_000L);
    }

    private static ToolRegistry registry() {
        return new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
    }

    private static RankMoviePlanTool realUnavailableShowtimeTool() {
        return new RankMoviePlanTool(new FixedRecommendationQueryService(
                () -> new FixedRecommendationCatalog(
                        "fixed-rec-v1", "FIXED_RECOMMENDATION", ContentSourceType.MOCK, 360L),
                query -> List.of(),
                Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static ExecutionPlan validatedPlan(
            ToolRegistry registry, FailurePolicy failurePolicy, boolean includeTimeRange) {
        return validatedPlanWithSlotValues(
                registry,
                failurePolicy,
                includeTimeRange,
                includeTimeRange
                        ? Map.of(
                                "movieId", "101",
                                "cinemaId", "201",
                                "date", "2026-08-04",
                                "timeFrom", "18:00",
                                "timeTo", "20:00")
                        : Map.of("movieId", "101", "cinemaId", "201", "date", "2026-08-04"));
    }

    private static ExecutionPlan validatedPlanWithSlotValues(
            ToolRegistry registry,
            FailurePolicy failurePolicy,
            boolean includeTimeRange,
            Map<String, String> slotValues) {
        var validation = new PlanSchemaValidator(registry).validate(
                candidatePlan(failurePolicy, includeTimeRange),
                new PlanValidationContext(
                        slotTypes(includeTimeRange), Map.of(), new SlotSnapshot(1L, slotValues)));
        return validation.executionPlan().orElseThrow();
    }

    private static CandidatePlan candidatePlan(FailurePolicy failurePolicy, boolean includeTimeRange) {
        List<InputReference> inputs = includeTimeRange
                ? List.of(
                        new InputReference("movieId", InputReferenceSource.SLOT, "movieId"),
                        new InputReference("cinemaId", InputReferenceSource.SLOT, "cinemaId"),
                        new InputReference("date", InputReferenceSource.SLOT, "date"),
                        new InputReference("timeFrom", InputReferenceSource.SLOT, "timeFrom"),
                        new InputReference("timeTo", InputReferenceSource.SLOT, "timeTo"))
                : List.of(
                        new InputReference("movieId", InputReferenceSource.SLOT, "movieId"),
                        new InputReference("cinemaId", InputReferenceSource.SLOT, "cinemaId"),
                        new InputReference("date", InputReferenceSource.SLOT, "date"));
        return candidatePlan(inputs, failurePolicy);
    }

    private static CandidatePlan candidatePlan(List<InputReference> inputs) {
        return candidatePlan(inputs, FailurePolicy.FAIL);
    }

    private static CandidatePlan candidatePlan(List<InputReference> inputs, FailurePolicy failurePolicy) {
        return new CandidatePlan(
                "plan-1",
                1,
                List.of(
                        new CandidatePlanNode(
                                "rank",
                                PlanNodeType.CALL_TOOL,
                                RankMoviePlanTool.TARGET_NAME,
                                inputs,
                                List.of(),
                                failurePolicy),
                        new CandidatePlanNode(
                                "render",
                                PlanNodeType.RENDER_RESULT,
                                null,
                                List.of(),
                                List.of("rank"),
                                FailurePolicy.FAIL)));
    }

    private static PlanValidationContext validValidationContext() {
        return new PlanValidationContext(slotTypes(false), Map.of(), new SlotSnapshot(
                1L, Map.of("movieId", "101", "cinemaId", "201", "date", "2026-08-04")));
    }

    private static Map<String, Class<?>> slotTypes(boolean includeTimeRange) {
        if (!includeTimeRange) {
            return Map.of("movieId", String.class, "cinemaId", String.class, "date", LocalDate.class);
        }
        return Map.of(
                "movieId", String.class,
                "cinemaId", String.class,
                "date", LocalDate.class,
                "timeFrom", java.time.LocalTime.class,
                "timeTo", java.time.LocalTime.class);
    }

    private static void assertInvalidParameter(boolean includeTimeRange, Map<String, String> slotValues) {
        assertInvalidParameter(
                includeTimeRange ? candidatePlan(FailurePolicy.FAIL, true).nodes().getFirst().inputRefs()
                        : candidatePlan(FailurePolicy.FAIL, false).nodes().getFirst().inputRefs(),
                slotValues);
    }

    private static void assertInvalidParameter(List<InputReference> inputs, Map<String, String> slotValues) {
        RankMoviePlanTool tool = mock(RankMoviePlanTool.class);
        ToolRegistry registry = registry();
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        boolean hasTimeRange = inputs.stream().anyMatch(input -> input.inputName().startsWith("time"));
        var validation = new PlanSchemaValidator(registry).validate(
                candidatePlan(inputs, FailurePolicy.FAIL),
                new PlanValidationContext(slotTypes(hasTimeRange), Map.of(), new SlotSnapshot(1L, slotValues)));
        RankMoviePlanExecutionAdapter adapter = new RankMoviePlanExecutionAdapter(tool, stateMachine);

        var result = adapter.execute(request(stateMachine.initialize(validation.executionPlan().orElseThrow())));

        verify(tool, never()).execute(any(), any());
        assertThat(result.toolResult().status()).isEqualTo(ToolStatus.FAILED);
        assertThat(result.toolResult().errorCode()).isEqualTo(100001);
        assertThat(result.toolResult().retryable()).isFalse();
        assertThat(result.state().nodeState("rank").status()).isEqualTo(PlanNodeStatus.FAILED);
        assertThat(result.state().nodeState("render").status()).isEqualTo(PlanNodeStatus.SKIPPED);
    }
}
