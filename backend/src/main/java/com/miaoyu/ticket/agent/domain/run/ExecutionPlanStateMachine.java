package com.miaoyu.ticket.agent.domain.run;

import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlan;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** 已校验计划的纯 Java 状态机，不调用工具、不创建确认动作。 */
public final class ExecutionPlanStateMachine {
    private static final int MAX_REPLAN_COUNT = 2;

    private final ToolRegistry toolRegistry;

    public ExecutionPlanStateMachine(ToolRegistry toolRegistry) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "工具白名单不能为空");
    }

    /** 创建一个与已校验运行计划对应的初始运行快照。 */
    public ExecutionRunState initialize(ExecutionPlan plan) {
        return ExecutionRunState.initial(plan);
    }

    /** 处理已阻塞分支后返回当前能够安全开始的全部节点。 */
    public RunnableNodeSelection selectRunnableNodes(ExecutionRunState state) {
        ExecutionRunState resolvedState = resolveBlockedNodes(Objects.requireNonNull(state, "运行状态不能为空"));
        List<ExecutionPlanNode> nodes = resolvedState.plan().nodes().stream()
                .filter(node -> isRunnable(node, resolvedState))
                .toList();
        return new RunnableNodeSelection(resolvedState, nodes);
    }

    /** 开始一个已被状态机选中的节点。 */
    public ExecutionRunState startNode(ExecutionRunState state, String nodeId) {
        RunnableNodeSelection selection = selectRunnableNodes(state);
        boolean selected = selection.nodes().stream().anyMatch(node -> node.nodeId().equals(nodeId));
        if (!selected) {
            throw new IllegalStateException("节点当前不可开始: " + nodeId);
        }
        return selection.state().withNodeState(selection.state().nodeState(nodeId).start());
    }

    /** 将执行中的非工具节点标记为成功。 */
    public ExecutionRunState succeedNode(ExecutionRunState state, String nodeId) {
        ExecutionRunState currentState = Objects.requireNonNull(state, "运行状态不能为空");
        return currentState.withNodeState(currentState.nodeState(nodeId).succeed(successResult()));
    }

    /** 将执行中的节点标记为失败，并跳过尚未开始的下游节点。 */
    public ExecutionRunState failNode(ExecutionRunState state, String nodeId) {
        ExecutionRunState currentState = Objects.requireNonNull(state, "运行状态不能为空");
        ExecutionRunState failedState = currentState.withNodeState(currentState.nodeState(nodeId).fail());
        return skipPendingDownstreamNodes(failedState, nodeId, nodeId);
    }

    /** 接收已登记只读工具的反馈，并按失败策略推进节点状态。 */
    public ExecutionRunState recordToolResult(ExecutionRunState state, String nodeId, ToolResult<?> result) {
        ExecutionRunState currentState = Objects.requireNonNull(state, "运行状态不能为空");
        ToolResult<?> toolResult = Objects.requireNonNull(result, "工具结果不能为空");
        ExecutionPlanNode node = findNode(currentState, nodeId);
        if (node.type() != PlanNodeType.CALL_TOOL || !isReadOnlyTool(node)) {
            throw new IllegalStateException("节点不是可执行的只读工具: " + nodeId);
        }
        ExecutionNodeState nodeState = currentState.nodeState(nodeId);
        return switch (toolResult.status()) {
            case SUCCESS -> currentState.withNodeState(nodeState.succeed(toolResult));
            case PROCESSING -> currentState.withNodeState(nodeState.processing(toolResult));
            case FAILED -> recordFailedToolResult(currentState, node, nodeState, toolResult);
        };
    }

    /** 为当前运行申请一次重规划额度，不生成新计划。 */
    public ReplanRequestResult requestReplan(ExecutionRunState state) {
        ExecutionRunState currentState = Objects.requireNonNull(state, "运行状态不能为空");
        if (currentState.replanCount() >= MAX_REPLAN_COUNT) {
            return new ReplanRequestResult(false, currentState);
        }
        return new ReplanRequestResult(true, currentState.withNextReplanCount());
    }

    private ExecutionRunState recordFailedToolResult(
            ExecutionRunState state,
            ExecutionPlanNode node,
            ExecutionNodeState nodeState,
            ToolResult<?> result) {
        if (node.failurePolicy() == FailurePolicy.RETRY_ONCE && result.retryable() && nodeState.retryCount() == 0) {
            return state.withNodeState(nodeState.retry(result));
        }
        ExecutionRunState failedState = state.withNodeState(nodeState.fail(result));
        return skipPendingDownstreamNodes(failedState, node.nodeId(), node.nodeId());
    }

    private ExecutionRunState resolveBlockedNodes(ExecutionRunState state) {
        ExecutionRunState resolvedState = state;
        for (ExecutionPlanNode node : state.plan().nodes()) {
            ExecutionNodeState nodeState = resolvedState.nodeState(node.nodeId());
            if (nodeState.status() != PlanNodeStatus.PENDING) {
                continue;
            }
            String sourceNodeId = blockedSourceNodeId(node, resolvedState);
            if (sourceNodeId != null) {
                resolvedState = skipPendingDownstreamNodes(resolvedState, node.nodeId(), sourceNodeId);
            }
        }
        return resolvedState;
    }

    private String blockedSourceNodeId(ExecutionPlanNode node, ExecutionRunState state) {
        for (String dependencyId : node.dependsOn()) {
            ExecutionNodeState dependencyState = state.nodeState(dependencyId);
            if (dependencyState.status() == PlanNodeStatus.FAILED) {
                return dependencyId;
            }
            if (dependencyState.status() == PlanNodeStatus.SKIPPED) {
                return dependencyState.skipSourceNodeId() == null ? dependencyId : dependencyState.skipSourceNodeId();
            }
        }
        return null;
    }

    private ExecutionRunState skipPendingDownstreamNodes(
            ExecutionRunState state, String firstNodeId, String sourceNodeId) {
        ExecutionRunState updatedState = state;
        Deque<String> pendingNodeIds = new ArrayDeque<>();
        pendingNodeIds.add(firstNodeId);
        while (!pendingNodeIds.isEmpty()) {
            String nodeId = pendingNodeIds.removeFirst();
            ExecutionNodeState nodeState = updatedState.nodeState(nodeId);
            if (nodeState.status() == PlanNodeStatus.PENDING) {
                updatedState = updatedState.withNodeState(nodeState.skipForUpstreamFailure(sourceNodeId));
            }
            for (ExecutionPlanNode node : updatedState.plan().nodes()) {
                if (node.dependsOn().contains(nodeId)) {
                    pendingNodeIds.addLast(node.nodeId());
                }
            }
        }
        return updatedState;
    }

    private boolean isRunnable(ExecutionPlanNode node, ExecutionRunState state) {
        if (state.nodeState(node.nodeId()).status() != PlanNodeStatus.PENDING
                || node.type() == PlanNodeType.CONFIRM_ACTION
                || node.requiresConfirmation()) {
            return false;
        }
        if (!node.dependsOn().stream()
                .allMatch(dependencyId -> state.nodeState(dependencyId).status() == PlanNodeStatus.SUCCESS)) {
            return false;
        }
        return node.type() != PlanNodeType.CALL_TOOL || isReadOnlyTool(node);
    }

    private boolean isReadOnlyTool(ExecutionPlanNode node) {
        if (node.targetName() == null || node.targetName().isBlank()) {
            return false;
        }
        return toolRegistry.find(node.targetName()).map(ToolDefinition::readOnly).orElse(false);
    }

    private static ExecutionPlanNode findNode(ExecutionRunState state, String nodeId) {
        return state.plan().nodes().stream()
                .filter(node -> node.nodeId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("计划中不存在节点: " + nodeId));
    }

    private static ToolResult<Void> successResult() {
        return new ToolResult<>(ToolStatus.SUCCESS, null, null, false, false, null, false, null, null, null, null);
    }
}
