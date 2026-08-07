package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.run.RunnableNodeSelection;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.travel.api.GetTravelAdviceCommand;
import com.miaoyu.ticket.travel.api.GetTravelAdviceTool;
import com.miaoyu.ticket.travel.api.TravelAdviceToolResult;
import java.util.List;
import java.util.Objects;

/**
 * 将 B 已确认的 {@code travelTaskId} 槽位安全转换为 D 的只读出行建议查询。
 *
 * <p>适配器不读取会话实体、不创建线程，也不解析模型常量；认证身份由 B 已配置的
 * {@code DelegatingSecurityContextExecutor} 保留，D Tool 继续通过当前认证上下文校验归属。</p>
 */
public final class GetTravelAdviceExecutionAdapter
        implements AgentToolExecutor<GetTravelAdviceCommand, TravelAdviceToolResult> {

    private static final String TRAVEL_TASK_ID_SLOT = "travelTaskId";

    private final GetTravelAdviceTool tool;
    private final ExecutionPlanStateMachine stateMachine;

    public GetTravelAdviceExecutionAdapter(GetTravelAdviceTool tool, ExecutionPlanStateMachine stateMachine) {
        this.tool = Objects.requireNonNull(tool, "出行建议 Tool 不能为空");
        this.stateMachine = Objects.requireNonNull(stateMachine, "执行状态机不能为空");
    }

    @Override
    public String targetName() {
        return GetTravelAdviceTool.TARGET_NAME;
    }

    @Override
    public ToolDefinition definition() {
        return AgentToolDefinitions.getTravelAdvice();
    }

    @Override
    public ToolResult<TravelAdviceToolResult> execute(ToolContext context, GetTravelAdviceCommand command) {
        return tool.execute(Objects.requireNonNull(context, "ToolContext 不能为空"),
                Objects.requireNonNull(command, "出行建议命令不能为空"));
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        ExecutionRequest executionRequest = Objects.requireNonNull(request, "执行请求不能为空");
        RunnableNodeSelection selection = stateMachine.selectRunnableNodes(executionRequest.state());
        ExecutionPlanNode node = requireRunnableNode(selection, executionRequest.nodeId());
        ExecutionRunState runningState = stateMachine.startNode(selection.state(), node.nodeId());
        ToolContext context = createContext(executionRequest, node);
        try {
            ToolResult<TravelAdviceToolResult> result = execute(context, commandFromConfirmedSlot(node));
            return new ExecutionResult(stateMachine.recordToolResult(runningState, node.nodeId(), result), result);
        } catch (IllegalArgumentException exception) {
            // 计划结构或槽位快照异常时不访问 D，避免模型绕过受控卡片动作。
            ToolResult<TravelAdviceToolResult> result = invalidInput(context);
            return new ExecutionResult(stateMachine.recordToolResult(runningState, node.nodeId(), result), result);
        }
    }

    private static ExecutionPlanNode requireRunnableNode(RunnableNodeSelection selection, String nodeId) {
        return selection.nodes().stream()
                .filter(node -> node.nodeId().equals(nodeId))
                .filter(node -> GetTravelAdviceTool.TARGET_NAME.equals(node.targetName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("节点当前不可执行 getTravelAdvice: " + nodeId));
    }

    private static GetTravelAdviceCommand commandFromConfirmedSlot(ExecutionPlanNode node) {
        if (node.slotSnapshot() == null || node.inputRefs().size() != 1) {
            throw new IllegalArgumentException("getTravelAdvice 必须只使用一个任务槽位");
        }
        InputReference reference = node.inputRefs().getFirst();
        if (!TRAVEL_TASK_ID_SLOT.equals(reference.inputName())
                || reference.source() != InputReferenceSource.SLOT
                || !TRAVEL_TASK_ID_SLOT.equals(reference.sourceId())) {
            throw new IllegalArgumentException("getTravelAdvice 只能读取 travelTaskId 槽位");
        }
        return new GetTravelAdviceCommand(node.slotSnapshot().values().get(TRAVEL_TASK_ID_SLOT));
    }

    private static ToolContext createContext(ExecutionRequest request, ExecutionPlanNode node) {
        long deadlineMs = Math.min(
                request.remainingDeadlineMs(), AgentToolDefinitions.TRAVEL_ADVICE_TIMEOUT.toMillis());
        return new ToolContext(request.runId(), node.nodeId(), GetTravelAdviceTool.TARGET_NAME,
                List.of("slots." + TRAVEL_TASK_ID_SLOT), deadlineMs, request.traceId(), null, null,
                node.slotSnapshot().version());
    }

    private static ToolResult<TravelAdviceToolResult> invalidInput(ToolContext context) {
        return new ToolResult<>(ToolStatus.FAILED, null, CommonErrorCode.INVALID_PARAMETER.code(), false, false,
                "CHECK_TRAVEL_TASK", false, null, context.stateVersion(), null, null);
    }
}
