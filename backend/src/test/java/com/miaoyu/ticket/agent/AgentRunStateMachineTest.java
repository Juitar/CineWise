package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.miaoyu.ticket.agent.domain.plan.ExecutionPlan;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.agent.domain.run.ExecutionNodeState;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.run.ReplanRequestResult;
import com.miaoyu.ticket.agent.domain.tool.ToolCommand;
import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Agent 运行状态机的纯单元测试。 */
class AgentRunStateMachineTest {

    @Test
    void shouldSelectIndependentReadOnlyNodesAndRejectUnsafeNodes() {
        ExecutionPlanStateMachine stateMachine = stateMachine();
        ExecutionRunState state = stateMachine.initialize(plan(
                node("read-a", PlanNodeType.CALL_TOOL, "readTool", List.of(), FailurePolicy.RETRY_ONCE),
                node("read-b", PlanNodeType.CALL_TOOL, "readTool", List.of(), FailurePolicy.RETRY_ONCE),
                node("dependent", PlanNodeType.COMPUTE, null, List.of("read-a"), FailurePolicy.FAIL),
                node("unknown", PlanNodeType.CALL_TOOL, "unknownTool", List.of(), FailurePolicy.FAIL),
                node("write", PlanNodeType.CALL_TOOL, "writeTool", List.of(), FailurePolicy.FAIL),
                node("confirm", PlanNodeType.CONFIRM_ACTION, null, List.of(), FailurePolicy.FAIL)));

        var selection = stateMachine.selectRunnableNodes(state);

        assertEquals(List.of("read-a", "read-b"), selection.nodes().stream().map(ExecutionPlanNode::nodeId).toList());
        assertEquals(PlanNodeStatus.PENDING, selection.state().nodeState("read-a").status());
        assertEquals(PlanNodeStatus.PENDING, selection.state().nodeState("dependent").status());
    }

    @Test
    void shouldAdvanceSuccessfulNodeAndRejectTerminalRestart() {
        ExecutionPlanStateMachine stateMachine = stateMachine();
        ExecutionRunState state = stateMachine.initialize(plan(
                node("read", PlanNodeType.CALL_TOOL, "readTool", List.of(), FailurePolicy.RETRY_ONCE),
                node("render", PlanNodeType.RENDER_RESULT, null, List.of("read"), FailurePolicy.FAIL)));

        state = stateMachine.startNode(state, "read");
        state = stateMachine.recordToolResult(state, "read", toolResult(ToolStatus.SUCCESS, false));

        assertEquals(PlanNodeStatus.SUCCESS, state.nodeState("read").status());
        assertEquals(1, state.nodeState("read").attemptCount());
        assertEquals(List.of("render"), stateMachine.selectRunnableNodes(state).nodes().stream()
                .map(ExecutionPlanNode::nodeId)
                .toList());
        ExecutionRunState completedState = state;
        assertThrows(IllegalStateException.class, () -> stateMachine.startNode(completedState, "read"));
    }

    @Test
    void shouldKeepProcessingToolNodeRunningWithoutConsumingRetry() {
        ExecutionPlanStateMachine stateMachine = stateMachine();
        ExecutionRunState state = stateMachine.initialize(
                plan(node("read", PlanNodeType.CALL_TOOL, "readTool", List.of(), FailurePolicy.RETRY_ONCE)));

        state = stateMachine.startNode(state, "read");
        state = stateMachine.recordToolResult(state, "read", toolResult(ToolStatus.PROCESSING, false));

        assertEquals(PlanNodeStatus.RUNNING, state.nodeState("read").status());
        assertEquals(1, state.nodeState("read").attemptCount());
        assertEquals(0, state.nodeState("read").retryCount());
    }

    @Test
    void shouldSkipTransitiveDownstreamNodesWithoutAffectingIndependentBranch() {
        ExecutionPlanStateMachine stateMachine = stateMachine();
        ExecutionRunState state = stateMachine.initialize(plan(
                node("failed", PlanNodeType.CALL_TOOL, "readTool", List.of(), FailurePolicy.FAIL),
                node("first-downstream", PlanNodeType.COMPUTE, null, List.of("failed"), FailurePolicy.FAIL),
                node(
                        "second-downstream",
                        PlanNodeType.RENDER_RESULT,
                        null,
                        List.of("first-downstream"),
                        FailurePolicy.FAIL),
                node("independent", PlanNodeType.COMPUTE, null, List.of(), FailurePolicy.FAIL)));

        state = stateMachine.startNode(state, "failed");
        state = stateMachine.recordToolResult(state, "failed", toolResult(ToolStatus.FAILED, false));

        assertEquals(PlanNodeStatus.FAILED, state.nodeState("failed").status());
        assertSkippedByFailure(state, "first-downstream", "failed");
        assertSkippedByFailure(state, "second-downstream", "failed");
        assertEquals(List.of("independent"), stateMachine.selectRunnableNodes(state).nodes().stream()
                .map(ExecutionPlanNode::nodeId)
                .toList());
    }

    @Test
    void shouldSkipNodesWhosePredecessorWasAlreadySkipped() {
        ExecutionPlanStateMachine stateMachine = stateMachine();
        ExecutionRunState state = stateMachine.initialize(plan(
                skippedNode("skipped", "root-failure"),
                node("blocked", PlanNodeType.COMPUTE, null, List.of("skipped"), FailurePolicy.FAIL),
                node("downstream", PlanNodeType.RENDER_RESULT, null, List.of("blocked"), FailurePolicy.FAIL)));

        var selection = stateMachine.selectRunnableNodes(state);

        assertTrue(selection.nodes().isEmpty());
        assertSkippedByFailure(selection.state(), "blocked", "root-failure");
        assertSkippedByFailure(selection.state(), "downstream", "root-failure");
    }

    @Test
    void shouldRetryOnlyOnceThenFailAndSkipDownstream() {
        ExecutionPlanStateMachine stateMachine = stateMachine();
        ExecutionRunState state = stateMachine.initialize(plan(
                node("read", PlanNodeType.CALL_TOOL, "readTool", List.of(), FailurePolicy.RETRY_ONCE),
                node("downstream", PlanNodeType.COMPUTE, null, List.of("read"), FailurePolicy.FAIL)));

        state = stateMachine.startNode(state, "read");
        state = stateMachine.recordToolResult(state, "read", toolResult(ToolStatus.FAILED, true));

        assertEquals(PlanNodeStatus.PENDING, state.nodeState("read").status());
        assertEquals(1, state.nodeState("read").attemptCount());
        assertEquals(1, state.nodeState("read").retryCount());

        state = stateMachine.startNode(state, "read");
        state = stateMachine.recordToolResult(state, "read", toolResult(ToolStatus.FAILED, true));

        assertEquals(PlanNodeStatus.FAILED, state.nodeState("read").status());
        assertEquals(2, state.nodeState("read").attemptCount());
        assertSkippedByFailure(state, "downstream", "read");
    }

    @Test
    void shouldFailNonRetryableToolAndNeverStartWriteTool() {
        ExecutionPlanStateMachine stateMachine = stateMachine();
        ExecutionRunState readState = stateMachine.initialize(
                plan(node("read", PlanNodeType.CALL_TOOL, "readTool", List.of(), FailurePolicy.RETRY_ONCE)));

        readState = stateMachine.startNode(readState, "read");
        readState = stateMachine.recordToolResult(readState, "read", toolResult(ToolStatus.FAILED, false));

        assertEquals(PlanNodeStatus.FAILED, readState.nodeState("read").status());
        ExecutionRunState writeState = stateMachine.initialize(
                plan(node("write", PlanNodeType.CALL_TOOL, "writeTool", List.of(), FailurePolicy.RETRY_ONCE)));
        assertThrows(IllegalStateException.class, () -> stateMachine.startNode(writeState, "write"));
    }

    @Test
    void shouldRejectDirectToolNodeCompletionOutsideToolResultHandler() {
        ExecutionPlanStateMachine stateMachine = stateMachine();
        ExecutionRunState runningState = stateMachine.startNode(
                stateMachine.initialize(
                        plan(node("read", PlanNodeType.CALL_TOOL, "readTool", List.of(), FailurePolicy.RETRY_ONCE))),
                "read");

        assertThrows(IllegalStateException.class, () -> stateMachine.succeedNode(runningState, "read"));
        assertThrows(IllegalStateException.class, () -> stateMachine.failNode(runningState, "read"));
        assertEquals(PlanNodeStatus.RUNNING, runningState.nodeState("read").status());
        assertEquals(0, runningState.nodeState("read").retryCount());
    }

    @Test
    void shouldApproveAtMostTwoReplansWithoutChangingPlan() {
        ExecutionPlanStateMachine stateMachine = stateMachine();
        ExecutionRunState state = stateMachine.initialize(
                plan(node("compute", PlanNodeType.COMPUTE, null, List.of(), FailurePolicy.REPLAN)));

        ReplanRequestResult first = stateMachine.requestReplan(state);
        ReplanRequestResult second = stateMachine.requestReplan(first.state());
        ReplanRequestResult third = stateMachine.requestReplan(second.state());

        assertTrue(first.approved());
        assertEquals(1, first.state().replanCount());
        assertTrue(second.approved());
        assertEquals(2, second.state().replanCount());
        assertFalse(third.approved());
        assertEquals(second.state(), third.state());
        assertEquals(state.plan(), third.state().plan());
    }

    @Test
    void shouldKeepRunStateImmutable() {
        ExecutionPlanStateMachine stateMachine = stateMachine();
        ExecutionRunState state = stateMachine.initialize(
                plan(node("compute", PlanNodeType.COMPUTE, null, List.of(), FailurePolicy.FAIL)));

        assertThrows(UnsupportedOperationException.class, () -> state.nodeStates().clear());
        assertTrue(Modifier.isPrivate(ExecutionRunState.class.getDeclaredConstructors()[0].getModifiers()));
        assertTrue(Modifier.isPrivate(ExecutionNodeState.class.getDeclaredConstructors()[0].getModifiers()));
    }

    @Test
    void shouldRejectForgedNodeStateCounters() throws ReflectiveOperationException {
        Constructor<ExecutionNodeState> constructor = ExecutionNodeState.class.getDeclaredConstructor(
                String.class,
                PlanNodeStatus.class,
                int.class,
                int.class,
                ToolStatus.class,
                Integer.class,
                boolean.class,
                String.class,
                String.class);
        constructor.setAccessible(true);

        InvocationTargetException noAttemptForSuccess = assertThrows(
                InvocationTargetException.class,
                () -> constructor.newInstance("node", PlanNodeStatus.SUCCESS, 0, 0, null, null, false, null, null));
        InvocationTargetException excessiveRetry = assertThrows(
                InvocationTargetException.class,
                () -> constructor.newInstance("node", PlanNodeStatus.PENDING, 1, 2, null, null, false, null, null));

        assertTrue(noAttemptForSuccess.getCause() instanceof IllegalArgumentException);
        assertTrue(excessiveRetry.getCause() instanceof IllegalArgumentException);
    }

    private static ExecutionPlanStateMachine stateMachine() {
        return new ExecutionPlanStateMachine(new ToolRegistry(List.of(readTool(), writeTool())));
    }

    private static ExecutionPlan plan(ExecutionPlanNode... nodes) {
        return new ExecutionPlan("plan-1", 1, List.of(nodes));
    }

    private static ExecutionPlanNode node(
            String nodeId, PlanNodeType type, String targetName, List<String> dependsOn, FailurePolicy failurePolicy) {
        return new ExecutionPlanNode(
                nodeId,
                type,
                targetName,
                List.of(),
                dependsOn,
                failurePolicy,
                PlanNodeStatus.PENDING,
                type == PlanNodeType.CONFIRM_ACTION,
                false,
                null,
                null,
                new SlotSnapshot(1L, Map.of()));
    }

    private static ExecutionPlanNode skippedNode(String nodeId, String sourceNodeId) {
        return new ExecutionPlanNode(
                nodeId,
                PlanNodeType.COMPUTE,
                null,
                List.of(),
                List.of(),
                FailurePolicy.FAIL,
                PlanNodeStatus.SKIPPED,
                false,
                true,
                "UPSTREAM_FAILED",
                sourceNodeId,
                new SlotSnapshot(1L, Map.of()));
    }

    private static ToolResult<String> toolResult(ToolStatus status, boolean retryable) {
        return new ToolResult<>(
                status,
                null,
                status == ToolStatus.SUCCESS ? null : 306003,
                retryable,
                false,
                null,
                false,
                null,
                null,
                null,
                null);
    }

    private static ToolDefinition readTool() {
        return new ToolDefinition(
                "readTool",
                ReadCommand.class,
                String.class,
                true,
                Duration.ofSeconds(3L),
                false,
                List.of(),
                Set.of(306003));
    }

    private static ToolDefinition writeTool() {
        return new ToolDefinition(
                "writeTool",
                WriteCommand.class,
                String.class,
                false,
                Duration.ofSeconds(3L),
                true,
                List.of(),
                Set.of(206006));
    }

    private static void assertSkippedByFailure(ExecutionRunState state, String nodeId, String sourceNodeId) {
        assertEquals(PlanNodeStatus.SKIPPED, state.nodeState(nodeId).status());
        assertTrue(state.nodeState(nodeId).autoSkipped());
        assertEquals("UPSTREAM_FAILED", state.nodeState(nodeId).skipReason());
        assertEquals(sourceNodeId, state.nodeState(nodeId).skipSourceNodeId());
    }

    private record ReadCommand() implements ToolCommand {
    }

    private record WriteCommand() implements ToolCommand {
    }
}
