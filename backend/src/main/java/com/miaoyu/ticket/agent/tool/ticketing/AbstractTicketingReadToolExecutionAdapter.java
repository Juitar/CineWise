package com.miaoyu.ticket.agent.tool.ticketing;

import com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.run.RunnableNodeSelection;
import com.miaoyu.ticket.agent.domain.tool.ToolCommand;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.time.DateTimeException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A 的票务只读 Adapter 公共执行骨架，固定 Agent 节点选择、预算收缩和状态机回写边界。 */
abstract class AbstractTicketingReadToolExecutionAdapter<C extends ToolCommand, R>
        implements ReadOnlyToolExecutionAdapter {

    private final String targetName;
    private final Duration timeout;
    private final Set<String> commandInputNames;
    private final ExecutionPlanStateMachine stateMachine;

    AbstractTicketingReadToolExecutionAdapter(
            String targetName,
            Duration timeout,
            Set<String> commandInputNames,
            ExecutionPlanStateMachine stateMachine) {
        this.targetName = Objects.requireNonNull(targetName, "工具名不能为空");
        this.timeout = Objects.requireNonNull(timeout, "工具超时不能为空");
        this.commandInputNames = Set.copyOf(commandInputNames);
        this.stateMachine = Objects.requireNonNull(stateMachine, "状态机不能为空");
    }

    @Override
    public final String targetName() {
        return targetName;
    }

    @Override
    public final ExecutionResult execute(ExecutionRequest request) {
        ExecutionRequest executionRequest = Objects.requireNonNull(request, "执行请求不能为空");
        RunnableNodeSelection selection = stateMachine.selectRunnableNodes(executionRequest.state());
        ExecutionPlanNode node = requireRunnableNode(selection, executionRequest.nodeId());
        ExecutionRunState runningState = stateMachine.startNode(selection.state(), node.nodeId());
        ToolContext context = createToolContext(executionRequest, node);

        try {
            C command = createCommand(node, indexSlotReferences(node));
            ToolResult<R> result = executeTool(context, command);
            return new ExecutionResult(stateMachine.recordToolResult(runningState, node.nodeId(), result), result);
        } catch (DateTimeException | IllegalArgumentException exception) {
            // 模型或旧计划的输入不合法时禁止访问票务服务，也不能把解析异常写入 Agent 事件。
            ToolResult<R> failure = invalidParameterResult(context);
            return new ExecutionResult(stateMachine.recordToolResult(runningState, node.nodeId(), failure), failure);
        }
    }

    protected abstract C createCommand(ExecutionPlanNode node, Map<String, InputReference> references);

    protected abstract ToolResult<R> executeTool(ToolContext context, C command);

    protected final String requiredSlotValue(
            ExecutionPlanNode node, Map<String, InputReference> references, String inputName) {
        InputReference reference = references.get(inputName);
        if (reference == null) {
            throw new IllegalArgumentException(targetName + " 缺少必填输入: " + inputName);
        }
        String value = node.slotSnapshot().values().get(reference.sourceId());
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(targetName + " 槽位值不能为空: " + inputName);
        }
        return value;
    }

    protected final String optionalSlotValue(
            ExecutionPlanNode node, Map<String, InputReference> references, String inputName) {
        InputReference reference = references.get(inputName);
        if (reference == null) {
            return null;
        }
        String value = node.slotSnapshot().values().get(reference.sourceId());
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(targetName + " 槽位值不能为空: " + inputName);
        }
        return value;
    }

    private ExecutionPlanNode requireRunnableNode(RunnableNodeSelection selection, String nodeId) {
        return selection.nodes().stream()
                .filter(node -> node.nodeId().equals(nodeId))
                .filter(node -> targetName.equals(node.targetName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("节点当前不可执行 " + targetName + ": " + nodeId));
    }

    private ToolContext createToolContext(ExecutionRequest request, ExecutionPlanNode node) {
        long deadlineMs = Math.min(request.remainingDeadlineMs(), timeout.toMillis());
        return new ToolContext(
                request.runId(),
                node.nodeId(),
                targetName,
                declaredSlotReferences(node),
                deadlineMs,
                request.traceId(),
                null,
                null,
                node.slotSnapshot().version());
    }

    private Map<String, InputReference> indexSlotReferences(ExecutionPlanNode node) {
        if (node.slotSnapshot() == null) {
            throw new IllegalArgumentException(targetName + " 节点缺少槽位快照");
        }
        Map<String, InputReference> references = new LinkedHashMap<>();
        for (InputReference reference : node.inputRefs()) {
            if (reference == null || !commandInputNames.contains(reference.inputName())) {
                throw new IllegalArgumentException(targetName + " 包含未知输入引用");
            }
            if (reference.source() != InputReferenceSource.SLOT
                    || reference.sourceId() == null
                    || reference.sourceId().isBlank()) {
                throw new IllegalArgumentException(targetName + " 输入必须来自有效槽位");
            }
            if (references.putIfAbsent(reference.inputName(), reference) != null) {
                throw new IllegalArgumentException(targetName + " 输入引用不能重复");
            }
        }
        return references;
    }

    private static List<String> declaredSlotReferences(ExecutionPlanNode node) {
        return node.inputRefs().stream()
                .filter(Objects::nonNull)
                .filter(reference -> reference.source() == InputReferenceSource.SLOT)
                .filter(reference -> reference.sourceId() != null && !reference.sourceId().isBlank())
                .map(reference -> "slots." + reference.sourceId())
                .toList();
    }

    private ToolResult<R> invalidParameterResult(ToolContext context) {
        return new ToolResult<>(
                ToolStatus.FAILED,
                null,
                CommonErrorCode.INVALID_PARAMETER.code(),
                false,
                false,
                "CHECK_INPUT",
                false,
                null,
                context.stateVersion(),
                null,
                null);
    }
}
