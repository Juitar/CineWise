package com.miaoyu.ticket.agent.domain.run;

import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlan;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * 已校验计划的纯 Java 状态机，不调用工具、不创建确认动作。
 *
 * <p>状态机只处理“哪个节点可开始、工具结果如何推进、失败后哪些下游必须跳过”。工具调用、会话保存、
 * SSE 推送和确认凭证都在它的职责外，避免纯状态规则依赖网络、数据库或其他模块实现。
 *
 * <p>所有入口返回新快照而不修改传入对象，使重试、审计和测试能够依据每次返回值判断状态变化。
 */
public final class ExecutionPlanStateMachine {
    /**
     * 重规划的最大次数。
     *
     * <p>该额度只限制状态层提出重规划请求，不能自动调用模型。限制次数能防止工具失败或计划错误时反复
     * 消耗模型与下游查询资源；真正如何生成新计划由外层应用服务决定。
     */
    private static final int MAX_REPLAN_COUNT = 2;

    private final ToolRegistry toolRegistry;

    public ExecutionPlanStateMachine(ToolRegistry toolRegistry) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "工具白名单不能为空");
    }

    /**
     * 创建一个与已校验运行计划对应的初始运行快照。
     *
     * <p>调用方只能传入 PlanSchemaValidator 产生的 ExecutionPlan。状态机不在这里补做结构校验，
     * 也不接受候选计划，防止未校验的模型节点直接进入运行状态。
     */
    public ExecutionRunState initialize(ExecutionPlan plan) {
        return ExecutionRunState.initial(plan);
    }

    /**
     * 处理已阻塞分支后返回当前能够安全开始的全部节点。
     *
     * <p>先解析因上游失败而不可达的 PENDING 节点，再挑选节点，避免调用方看到一个随后会被跳过的
     * 节点并错误开始执行。返回的 selection 同时包含解析后的状态，调用方必须使用该快照继续推进。
     */
    public RunnableNodeSelection selectRunnableNodes(ExecutionRunState state) {
        ExecutionRunState resolvedState = resolveBlockedNodes(Objects.requireNonNull(state, "运行状态不能为空"));
        List<ExecutionPlanNode> nodes = resolvedState.plan().nodes().stream()
                .filter(node -> isRunnable(node, resolvedState))
                .toList();
        return new RunnableNodeSelection(resolvedState, nodes);
    }

    /**
     * 开始一个已被状态机选中的节点。
     *
     * <p>不能直接把任意 PENDING 节点改为 RUNNING；必须再次经过 selectRunnableNodes，确保依赖成功、
     * 非确认节点且工具仍是只读。这样外层无法绕过失败分支或确认要求。
     */
    public ExecutionRunState startNode(ExecutionRunState state, String nodeId) {
        RunnableNodeSelection selection = selectRunnableNodes(state);
        boolean selected = selection.nodes().stream().anyMatch(node -> node.nodeId().equals(nodeId));
        if (!selected) {
            // 不可开始可能是依赖未完成、确认节点或工具不在白名单；统一拒绝而不是猜测原因并推进。
            throw new IllegalStateException("节点当前不可开始: " + nodeId);
        }
        return selection.state().withNodeState(selection.state().nodeState(nodeId).start());
    }

    /**
     * 将执行中的非工具节点标记为成功。
     *
     * <p>工具节点必须经 recordToolResult 记录 ToolStatus、错误码和重试信息。若允许这里直接成功，
     * 调用方就能伪造工具完成并让下游渲染不存在的推荐结果。
     */
    public ExecutionRunState succeedNode(ExecutionRunState state, String nodeId) {
        ExecutionRunState currentState = Objects.requireNonNull(state, "运行状态不能为空");
        requireNonToolNode(currentState, nodeId);
        return currentState.withNodeState(currentState.nodeState(nodeId).succeed());
    }

    /**
     * 将执行中的节点标记为失败，并跳过尚未开始的下游节点。
     *
     * <p>失败传播只影响尚未开始的下游。已经运行或已结束的节点不被回写，防止状态机覆盖真实工具结果；
     * 这些异常并发情况应由外层运行协调处理。
     */
    public ExecutionRunState failNode(ExecutionRunState state, String nodeId) {
        ExecutionRunState currentState = Objects.requireNonNull(state, "运行状态不能为空");
        requireNonToolNode(currentState, nodeId);
        ExecutionRunState failedState = currentState.withNodeState(currentState.nodeState(nodeId).fail());
        return skipPendingDownstreamNodes(failedState, nodeId, nodeId);
    }

    /**
     * 接收已登记只读工具的反馈，并按失败策略推进节点状态。
     *
     * <p>工具结果必须由类型化适配器产生。PROCESSING 保持 RUNNING，表示结果未知而不是失败；FAILED
     * 是否重试完全由计划中的 FailurePolicy 与 retryable 字段决定，状态机不自行调用工具。
     */
    public ExecutionRunState recordToolResult(ExecutionRunState state, String nodeId, ToolResult<?> result) {
        ExecutionRunState currentState = Objects.requireNonNull(state, "运行状态不能为空");
        ToolResult<?> toolResult = Objects.requireNonNull(result, "工具结果不能为空");
        ExecutionPlanNode node = findNode(currentState, nodeId);
        if (node.type() != PlanNodeType.CALL_TOOL || !isReadOnlyTool(node)) {
            // 写工具和未登记目标不能用“返回结果”绕过确认与白名单边界。
            throw new IllegalStateException("节点不是可执行的只读工具: " + nodeId);
        }
        ExecutionNodeState nodeState = currentState.nodeState(nodeId);
        return switch (toolResult.status()) {
            // SUCCESS 的业务数据仍留在 ToolResult，状态机只记录状态元数据。
            case SUCCESS -> currentState.withNodeState(nodeState.succeed(toolResult));
            // PROCESSING 不启动自动重试，调用方必须按未来明确的查询/恢复协议处理未知结果。
            case PROCESSING -> currentState.withNodeState(nodeState.processing(toolResult));
            // FAILED 的重试次数与下游跳过规则集中在一个分支，避免不同适配器各自实现。
            case FAILED -> recordFailedToolResult(currentState, node, nodeState, toolResult);
        };
    }

    /**
     * 为当前运行申请一次重规划额度，不生成新计划。
     *
     * <p>返回 accepted=false 时状态原样保留，调用方不得以失败为由重置计数。额度不是模型调用许可，
     * 后续生成的新候选仍需经过完整计划校验。
     */
    public ReplanRequestResult requestReplan(ExecutionRunState state) {
        ExecutionRunState currentState = Objects.requireNonNull(state, "运行状态不能为空");
        if (currentState.replanCount() >= MAX_REPLAN_COUNT) {
            // 达到上限后不抛异常，调用方可以把稳定拒绝结果写入自己的错误回复或审计记录。
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
            // 只读工具最多自动重试一次；没有 retryable 标记或已重试过都必须进入最终失败路径。
            return state.withNodeState(nodeState.retry(result));
        }
        // 最终失败后立刻标记所有尚未开始的后继节点，防止它们读取不存在或错误的上游结果。
        ExecutionRunState failedState = state.withNodeState(nodeState.fail(result));
        return skipPendingDownstreamNodes(failedState, node.nodeId(), node.nodeId());
    }

    private ExecutionRunState resolveBlockedNodes(ExecutionRunState state) {
        ExecutionRunState resolvedState = state;
        for (ExecutionPlanNode node : state.plan().nodes()) {
            ExecutionNodeState nodeState = resolvedState.nodeState(node.nodeId());
            if (nodeState.status() != PlanNodeStatus.PENDING) {
                // 已运行、成功、失败或已跳过的节点不回退，状态机只向前推进。
                continue;
            }
            String sourceNodeId = blockedSourceNodeId(node, resolvedState);
            if (sourceNodeId != null) {
                // 传入最初失败来源，便于整个被跳过分支追溯同一个根因。
                resolvedState = skipPendingDownstreamNodes(resolvedState, node.nodeId(), sourceNodeId);
            }
        }
        return resolvedState;
    }

    private String blockedSourceNodeId(ExecutionPlanNode node, ExecutionRunState state) {
        for (String dependencyId : node.dependsOn()) {
            ExecutionNodeState dependencyState = state.nodeState(dependencyId);
            if (dependencyState.status() == PlanNodeStatus.FAILED) {
                // 直接失败依赖是最清晰的根因，优先返回给下游跳过记录。
                return dependencyId;
            }
            if (dependencyState.status() == PlanNodeStatus.SKIPPED) {
                // 已跳过节点继续传递其最初失败来源，避免根因在多层传播中丢失。
                return dependencyState.skipSourceNodeId() == null ? dependencyId : dependencyState.skipSourceNodeId();
            }
        }
        return null;
    }

    private ExecutionRunState skipPendingDownstreamNodes(
            ExecutionRunState state, String firstNodeId, String sourceNodeId) {
        ExecutionRunState updatedState = state;
        Deque<String> pendingNodeIds = new ArrayDeque<>();
        // firstNodeId 本身可能仍 PENDING（resolveBlockedNodes）或已 FAILED（工具失败）；循环同时覆盖两种情况。
        pendingNodeIds.add(firstNodeId);
        while (!pendingNodeIds.isEmpty()) {
            String nodeId = pendingNodeIds.removeFirst();
            ExecutionNodeState nodeState = updatedState.nodeState(nodeId);
            if (nodeState.status() == PlanNodeStatus.PENDING) {
                // 只跳过尚未开始节点，绝不覆盖可能已由并行调用完成的节点状态。
                updatedState = updatedState.withNodeState(nodeState.skipForUpstreamFailure(sourceNodeId));
            }
            for (ExecutionPlanNode node : updatedState.plan().nodes()) {
                if (node.dependsOn().contains(nodeId)) {
                    // 继续向所有直接后继传播；队列和节点状态检查共同防止重复访问造成重复写入。
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
            // 确认节点和需要确认的节点永远不由只读状态机自动开始。
            return false;
        }
        if (!node.dependsOn().stream()
                .allMatch(dependencyId -> state.nodeState(dependencyId).status() == PlanNodeStatus.SUCCESS)) {
            // 依赖必须全部成功；SKIPPED、FAILED 与 RUNNING 都不能被当成可用的上游输出。
            return false;
        }
        // 非工具节点由主控推进；工具节点仅在注册表仍明确为只读时才可被选择。
        return node.type() != PlanNodeType.CALL_TOOL || isReadOnlyTool(node);
    }

    private boolean isReadOnlyTool(ExecutionPlanNode node) {
        if (node.targetName() == null || node.targetName().isBlank()) {
            // 目标为空的 CALL_TOOL 不具备执行身份，不能因节点类型正确就被放行。
            return false;
        }
        // 不存在的工具与写工具一律返回 false，不能在状态层映射成“已完成”。
        return toolRegistry.find(node.targetName()).map(ToolDefinition::readOnly).orElse(false);
    }

    private static ExecutionPlanNode findNode(ExecutionRunState state, String nodeId) {
        // 所有外部 nodeId 都先在当前快照查找，禁止使用旧计划节点操作新运行状态。
        return state.plan().nodes().stream()
                .filter(node -> node.nodeId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("计划中不存在节点: " + nodeId));
    }

    private static void requireNonToolNode(ExecutionRunState state, String nodeId) {
        if (findNode(state, nodeId).type() == PlanNodeType.CALL_TOOL) {
            // 该限制保留工具结果的错误码与重试语义，不能由通用成功/失败方法抹掉。
            throw new IllegalStateException("工具节点只能通过 recordToolResult 推进: " + nodeId);
        }
    }
}
