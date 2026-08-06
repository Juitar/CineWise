package com.miaoyu.ticket.agent.application.run;

import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.model.PlanGenerationRequest;
import com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationResult;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.run.RunnableNodeSelection;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * B 的多工具计划主控。
 *
 * <p>模型只产生候选计划；工具白名单、输入引用、依赖和写确认都在服务端校验。当前已确认的只读适配器
 * 只有 {@code rankMoviePlan}，因此未知只读工具不会被字符串路由，而是在任何调用前安全拒绝。</p>
 */
public final class MultiToolSupervisor {
    private static final String SAFE_PLAN_REJECTED = "PLAN_REJECTED";
    private static final String SAFE_AWAITING_CONFIRMATION = "AWAITING_CONFIRMATION";
    private static final String SAFE_PROCESSING = "RESULT_PROCESSING";

    private final ModelGateway modelGateway;
    private final ToolRegistry toolRegistry;
    private final PlanSchemaValidator planSchemaValidator;
    private final ExecutionPlanStateMachine stateMachine;
    private final java.util.Map<String, ReadOnlyToolExecutionAdapter> readOnlyAdapters;

    public MultiToolSupervisor(
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            PlanSchemaValidator planSchemaValidator,
            ExecutionPlanStateMachine stateMachine,
            List<ReadOnlyToolExecutionAdapter> readOnlyAdapters) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "模型网关不能为空");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "工具白名单不能为空");
        this.planSchemaValidator = Objects.requireNonNull(planSchemaValidator, "计划校验器不能为空");
        this.stateMachine = Objects.requireNonNull(stateMachine, "状态机不能为空");
        this.readOnlyAdapters = readOnlyAdapters.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                ReadOnlyToolExecutionAdapter::targetName, adapter -> adapter));
    }

    /** 生成、校验并执行本轮可运行的只读节点；写节点始终保留给既有确认动作服务。 */
    public MultiToolSupervisorResult run(MultiToolSupervisorRequest request) {
        MultiToolSupervisorRequest supervisorRequest = Objects.requireNonNull(request, "请求不能为空");
        var generated = modelGateway.generatePlan(new PlanGenerationRequest(
                supervisorRequest.clientRequestId(),
                supervisorRequest.input(),
                supervisorRequest.validationContext().slotSnapshot().values(),
                toolRegistry.definitions().keySet()));
        PlanValidationResult validation = planSchemaValidator.validate(
                generated.candidatePlan(), supervisorRequest.validationContext());
        if (!validation.isValid()) {
            return new MultiToolSupervisorResult(
                    generated.candidatePlan(), validation, null, List.of(), false, SAFE_PLAN_REJECTED);
        }
        ExecutionRunState state = stateMachine.initialize(validation.executionPlan().orElseThrow());
        List<MultiToolSupervisorResult.NodeToolResult> results = new ArrayList<>();
        int limit = Math.max(1, state.plan().nodes().size() * 2);
        for (int handled = 0; handled < limit; handled++) {
            RunnableNodeSelection selection = stateMachine.selectRunnableNodes(state);
            state = selection.state();
            if (selection.nodes().isEmpty()) {
                return completed(generated.candidatePlan(), validation, state, results);
            }
            // 每轮从快照选择一个节点；下一轮重新计算可运行集合，避免使用旧 selection 重复执行。
            ExecutionPlanNode node = selection.nodes().getFirst();
            ReadOnlyToolExecutionAdapter adapter = readOnlyAdapters.get(node.targetName());
            if (adapter == null) {
                throw new IllegalStateException(
                        "缺少已登记的只读工具适配器: " + node.targetName());
            }
            ReadOnlyToolExecutionAdapter.ExecutionRequest executionRequest =
                    new ReadOnlyToolExecutionAdapter.ExecutionRequest(
                    state, node.nodeId(), supervisorRequest.runId(), supervisorRequest.traceId(),
                    supervisorRequest.remainingDeadlineMs());
            ReadOnlyToolExecutionAdapter.ExecutionResult executed = adapter.execute(executionRequest);
            state = executed.state();
            results.add(new MultiToolSupervisorResult.NodeToolResult(node.nodeId(), executed.toolResult()));
            if (executed.toolResult().status() == ToolStatus.PROCESSING) {
                return new MultiToolSupervisorResult(
                        generated.candidatePlan(), validation, state, results, false, SAFE_PROCESSING);
            }
        }
        throw new IllegalStateException("计划调度超过节点重试上限");
    }

    /**
     * 请求模型给出替代候选计划并接受更高版本。
     *
     * <p>本方法只改变内存运行快照；调用方必须先用运行版本 CAS 保存返回状态，才可以发布新计划事件。
     * 旧节点结果在 CAS 失败时不得重试写入新计划。</p>
     */
    public MultiToolSupervisorResult replan(
            MultiToolSupervisorRequest request, MultiToolSupervisorResult previousResult) {
        MultiToolSupervisorRequest supervisorRequest = Objects.requireNonNull(request, "请求不能为空");
        MultiToolSupervisorResult previous = Objects.requireNonNull(previousResult, "前一运行结果不能为空");
        if (previous.state() == null) {
            throw new IllegalStateException("未通过校验的计划不能重规划");
        }
        var generated = modelGateway.generatePlan(new PlanGenerationRequest(
                supervisorRequest.clientRequestId(), supervisorRequest.input(),
                supervisorRequest.validationContext().slotSnapshot().values(), toolRegistry.definitions().keySet()));
        PlanValidationResult validation = planSchemaValidator.validate(
                generated.candidatePlan(), supervisorRequest.validationContext());
        if (!validation.isValid()) {
            return new MultiToolSupervisorResult(
                    generated.candidatePlan(), validation, previous.state(), previous.toolResults(),
                    previous.awaitingConfirmation(), SAFE_PLAN_REJECTED);
        }
        ExecutionRunState replanned = stateMachine.acceptReplan(
                previous.state(), validation.executionPlan().orElseThrow());
        return new MultiToolSupervisorResult(
                generated.candidatePlan(), validation, replanned, previous.toolResults(), false, null);
    }

    private static MultiToolSupervisorResult completed(
            com.miaoyu.ticket.agent.domain.plan.CandidatePlan candidatePlan,
            PlanValidationResult validation,
            ExecutionRunState state,
            List<MultiToolSupervisorResult.NodeToolResult> results) {
        boolean awaitingConfirmation = state.plan().nodes().stream()
                .anyMatch(node -> node.requiresConfirmation()
                        && state.nodeState(node.nodeId()).status()
                        == com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.PENDING);
        return new MultiToolSupervisorResult(
                candidatePlan, validation, state, results, awaitingConfirmation,
                awaitingConfirmation ? SAFE_AWAITING_CONFIRMATION : null);
    }
}
