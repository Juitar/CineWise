package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.tool.AgentToolDefinitions;
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
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.agent.tool.ticketing.QueryAvailableDatesExecutionAdapter;
import com.miaoyu.ticket.agent.tool.ticketing.QueryShowsExecutionAdapter;
import com.miaoyu.ticket.ticketing.api.QueryAvailableDatesTool;
import com.miaoyu.ticket.ticketing.api.QueryAvailableDatesToolCommand;
import com.miaoyu.ticket.ticketing.api.QueryAvailableDatesToolResult;
import com.miaoyu.ticket.ticketing.api.QueryShowsTool;
import com.miaoyu.ticket.ticketing.api.QueryShowsToolCommand;
import com.miaoyu.ticket.ticketing.api.QueryShowsToolResult;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** A 的票务 Agent 只读 Adapter 测试，不启动 Controller、SSE 或持久化设施。 */
class TicketingReadToolExecutionAdapterTest {

    @Test
    void shouldRegisterTwoTicketingReadToolsWithOnlyPublicErrors() {
        ToolRegistry registry = registry();

        var dates = registry.find(QueryAvailableDatesTool.TARGET_NAME).orElseThrow();
        var shows = registry.find(QueryShowsTool.TARGET_NAME).orElseThrow();

        assertThat(dates.readOnly()).isTrue();
        assertThat(dates.timeout()).isEqualTo(AgentToolDefinitions.TICKETING_READ_TIMEOUT);
        assertThat(dates.inputs()).extracting("name").containsExactly("movieId", "cinemaId");
        assertThat(dates.exposedErrorCodes()).containsExactlyInAnyOrder(100001, 306003);
        assertThat(shows.readOnly()).isTrue();
        assertThat(shows.inputs()).extracting("name")
                .containsExactly("movieId", "cinemaId", "businessDate", "timeFrom", "timeTo");
        assertThat(shows.exposedErrorCodes()).containsExactlyInAnyOrder(100001, 306003);
    }

    @Test
    void shouldConvertAvailableDateSlotsAndShrinkBudgetWithoutIdentityOrWriteKeys() {
        QueryAvailableDatesTool tool = mock(QueryAvailableDatesTool.class);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry());
        QueryAvailableDatesExecutionAdapter adapter = new QueryAvailableDatesExecutionAdapter(tool, stateMachine);
        when(tool.execute(any(), any())).thenReturn(successDates());

        var request = new com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionRequest(
                stateMachine.initialize(validatedDatesPlan(Map.of("movieId", "101", "cinemaId", "201"))),
                "dates",
                "run-1",
                "trace-1",
                9_000L);
        var result = adapter.execute(request);

        ArgumentCaptor<ToolContext> contextCaptor = ArgumentCaptor.forClass(ToolContext.class);
        ArgumentCaptor<QueryAvailableDatesToolCommand> commandCaptor =
                ArgumentCaptor.forClass(QueryAvailableDatesToolCommand.class);
        verify(tool).execute(contextCaptor.capture(), commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isEqualTo(new QueryAvailableDatesToolCommand("101", "201"));
        assertThat(contextCaptor.getValue().deadlineMs()).isEqualTo(5_000L);
        assertThat(contextCaptor.getValue().clientRequestId()).isNull();
        assertThat(contextCaptor.getValue().idempotencyKey()).isNull();
        assertThat(contextCaptor.getValue().inputRefs()).containsExactly("slots.movieId", "slots.cinemaId");
        assertThat(result.state().nodeState("dates").status()).isEqualTo(PlanNodeStatus.SUCCESS);
    }

    @Test
    void shouldRejectInvalidShowSlotsBeforeCallingTicketingTool() {
        QueryShowsTool tool = mock(QueryShowsTool.class);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry());
        QueryShowsExecutionAdapter adapter = new QueryShowsExecutionAdapter(tool, stateMachine);

        var request = new com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionRequest(
                stateMachine.initialize(validatedShowsPlan(Map.of(
                        "movieId", "101", "cinemaId", "201", "businessDate", "not-a-date"))),
                "shows",
                "run-2",
                "trace-2",
                2_000L);
        var result = adapter.execute(request);

        verify(tool, never()).execute(any(), any());
        assertThat(result.toolResult().status()).isEqualTo(ToolStatus.FAILED);
        assertThat(result.toolResult().errorCode()).isEqualTo(100001);
        assertThat(result.toolResult().retryable()).isFalse();
        assertThat(result.state().nodeState("shows").status()).isEqualTo(PlanNodeStatus.FAILED);
    }

    @Test
    void shouldConvertShowTimeWindowForPublicToolApi() {
        QueryShowsTool tool = mock(QueryShowsTool.class);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry());
        QueryShowsExecutionAdapter adapter = new QueryShowsExecutionAdapter(tool, stateMachine);
        when(tool.execute(any(), any())).thenReturn(successShows());

        adapter.execute(new com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionRequest(
                stateMachine.initialize(validatedShowsPlan(Map.of(
                        "movieId", "101", "cinemaId", "201", "businessDate", "2026-08-08",
                        "timeFrom", "18:00", "timeTo", "23:00"))),
                "shows", "run-3", "trace-3", 1_000L));

        ArgumentCaptor<QueryShowsToolCommand> commandCaptor = ArgumentCaptor.forClass(QueryShowsToolCommand.class);
        verify(tool).execute(any(), commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isEqualTo(new QueryShowsToolCommand(
                "101", "201", LocalDate.of(2026, 8, 8), LocalTime.of(18, 0), LocalTime.of(23, 0)));
    }

    private static ToolRegistry registry() {
        return new ToolRegistry(List.of(
                AgentToolDefinitions.queryAvailableDates(), AgentToolDefinitions.queryShows()));
    }

    private static ExecutionPlan validatedDatesPlan(Map<String, String> slots) {
        return validate(new CandidatePlan("dates-plan", 1, List.of(new CandidatePlanNode(
                "dates", PlanNodeType.CALL_TOOL, QueryAvailableDatesTool.TARGET_NAME,
                List.of(
                        new InputReference("movieId", InputReferenceSource.SLOT, "movieId"),
                        new InputReference("cinemaId", InputReferenceSource.SLOT, "cinemaId")),
                List.of(), FailurePolicy.FAIL))),
                Map.of("movieId", String.class, "cinemaId", String.class), slots);
    }

    private static ExecutionPlan validatedShowsPlan(Map<String, String> slots) {
        List<InputReference> references = new java.util.ArrayList<>(List.of(
                new InputReference("movieId", InputReferenceSource.SLOT, "movieId"),
                new InputReference("cinemaId", InputReferenceSource.SLOT, "cinemaId"),
                new InputReference("businessDate", InputReferenceSource.SLOT, "businessDate")));
        if (slots.containsKey("timeFrom")) {
            references.add(new InputReference("timeFrom", InputReferenceSource.SLOT, "timeFrom"));
        }
        if (slots.containsKey("timeTo")) {
            references.add(new InputReference("timeTo", InputReferenceSource.SLOT, "timeTo"));
        }
        return validate(new CandidatePlan("shows-plan", 1, List.of(new CandidatePlanNode(
                "shows",
                PlanNodeType.CALL_TOOL,
                QueryShowsTool.TARGET_NAME,
                references,
                List.of(),
                FailurePolicy.FAIL))),
                Map.of(
                        "movieId", String.class, "cinemaId", String.class, "businessDate", LocalDate.class,
                        "timeFrom", LocalTime.class, "timeTo", LocalTime.class),
                slots);
    }

    private static ExecutionPlan validate(
            CandidatePlan plan, Map<String, Class<?>> slotTypes, Map<String, String> slots) {
        return new PlanSchemaValidator(registry()).validate(
                plan, new PlanValidationContext(slotTypes, Map.of(), new SlotSnapshot(7L, slots)))
                .executionPlan()
                .orElseThrow();
    }

    private static ToolResult<QueryAvailableDatesToolResult> successDates() {
        return new ToolResult<>(ToolStatus.SUCCESS, new QueryAvailableDatesToolResult(List.of()), null,
                false, false, "CHOOSE_DATE", false, null, 7L, null, null);
    }

    private static ToolResult<QueryShowsToolResult> successShows() {
        return new ToolResult<>(ToolStatus.SUCCESS, new QueryShowsToolResult(List.of()), null,
                false, false, "VIEW_SHOWS", false, null, 7L, null, null);
    }
}
