package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.tool.AgentToolDefinitions;
import com.miaoyu.ticket.agent.application.tool.GetTravelAdviceExecutionAdapter;
import com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter;
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
import com.miaoyu.ticket.travel.api.GetTravelAdviceCommand;
import com.miaoyu.ticket.travel.api.GetTravelAdviceTool;
import com.miaoyu.ticket.travel.api.TravelAdviceToolResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证 Agent 只能使用卡片动作确认后的 travelTaskId 槽位调用 D 的只读建议 Tool。 */
class GetTravelAdviceExecutionAdapterTest {

    @Test
    void shouldReadOnlyConfirmedTravelTaskSlotAndReturnStructuredResult() {
        GetTravelAdviceTool tool = mock(GetTravelAdviceTool.class);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry());
        GetTravelAdviceExecutionAdapter adapter = new GetTravelAdviceExecutionAdapter(tool, stateMachine);
        when(tool.execute(any(), any())).thenReturn(success());

        var result = adapter.execute(new ReadOnlyToolExecutionAdapter.ExecutionRequest(
                stateMachine.initialize(validatedPlan("90001")), "advice", "run-1", "trace-1", 9_000L));

        ArgumentCaptor<ToolContext> context = ArgumentCaptor.forClass(ToolContext.class);
        ArgumentCaptor<GetTravelAdviceCommand> command = ArgumentCaptor.forClass(GetTravelAdviceCommand.class);
        verify(tool).execute(context.capture(), command.capture());
        assertThat(command.getValue()).isEqualTo(new GetTravelAdviceCommand("90001"));
        assertThat(context.getValue().inputRefs()).containsExactly("slots.travelTaskId");
        assertThat(context.getValue().deadlineMs()).isEqualTo(3_000L);
        assertThat(result.toolResult().data()).isInstanceOf(TravelAdviceToolResult.class);
        assertThat(((TravelAdviceToolResult) result.toolResult().data()).advice()).hasSize(1);
        assertThat(result.state().nodeState("advice").status()).isEqualTo(PlanNodeStatus.SUCCESS);
    }

    @Test
    void shouldRejectPlanThatDoesNotReferenceConfirmedTravelTaskSlot() {
        CandidatePlan plan = new CandidatePlan("invalid", 1, List.of(new CandidatePlanNode(
                "advice", PlanNodeType.CALL_TOOL, GetTravelAdviceTool.TARGET_NAME,
                List.of(new InputReference("travelTaskId", InputReferenceSource.SLOT, "otherTaskId")),
                List.of(), FailurePolicy.FAIL)));
        var validation = new PlanSchemaValidator(registry()).validate(plan,
                new PlanValidationContext(Map.of("travelTaskId", String.class), Map.of(),
                        new SlotSnapshot(7L, Map.of("otherTaskId", "90001"))));

        assertThat(validation.isValid()).isFalse();
    }

    private static ToolRegistry registry() {
        return new ToolRegistry(List.of(AgentToolDefinitions.getTravelAdvice()));
    }

    private static ExecutionPlan validatedPlan(String taskId) {
        return validate(new CandidatePlan("advice", 1, List.of(new CandidatePlanNode(
                "advice", PlanNodeType.CALL_TOOL, GetTravelAdviceTool.TARGET_NAME,
                List.of(new InputReference("travelTaskId", InputReferenceSource.SLOT, "travelTaskId")),
                List.of(), FailurePolicy.FAIL))), Map.of("travelTaskId", taskId));
    }

    private static ExecutionPlan validate(CandidatePlan plan, Map<String, String> slots) {
        return new PlanSchemaValidator(registry()).validate(plan,
                new PlanValidationContext(Map.of("travelTaskId", String.class), Map.of(), new SlotSnapshot(7L, slots)))
                .executionPlan().orElseThrow();
    }

    private static ToolResult<TravelAdviceToolResult> success() {
        Instant dataAt = Instant.parse("2026-08-07T10:00:00Z");
        TravelAdviceToolResult result = new TravelAdviceToolResult(true, "90001", "READY", null,
                List.of(new TravelAdviceToolResult.Advice("TRANSPORT", "提前出发")), "DEMO_WEATHER_V1",
                OffsetDateTime.parse("2026-08-07T10:00:00Z"), OffsetDateTime.parse("2026-08-07T10:30:00Z"),
                false, true, "DEMO");
        return new ToolResult<>(ToolStatus.SUCCESS, result, null, false, false, "RENDER_RESULT", true, "DEMO", 7L,
                dataAt, dataAt.plusSeconds(1800));
    }
}
