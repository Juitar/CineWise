package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.run.RunnableNodeSelection;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanCommand;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * B 到 D 的窄类型化调用适配器。
 *
 * <p>它只执行已校验的 {@code rankMoviePlan} 节点。计划生成、SSE、会话、持久化和重规划继续由后续
 * B 用例负责，D 工具也不会拿到这些对象。</p>
 */
public final class RankMoviePlanExecutionAdapter {
    private static final Set<String> COMMAND_INPUT_NAMES =
            Set.of("movieId", "cinemaId", "date", "timeFrom", "timeTo");

    private final RankMoviePlanTool rankMoviePlanTool;
    private final ExecutionPlanStateMachine stateMachine;

    public RankMoviePlanExecutionAdapter(
            RankMoviePlanTool rankMoviePlanTool, ExecutionPlanStateMachine stateMachine) {
        this.rankMoviePlanTool = Objects.requireNonNull(rankMoviePlanTool, "推荐工具不能为空");
        this.stateMachine = Objects.requireNonNull(stateMachine, "运行状态机不能为空");
    }

    /**
     * 执行一个当前可开始的推荐节点，并将唯一结果交回既有状态机。
     *
     * <p>输入转换失败时不调用 D，而是用统一的参数错误结果结束本次节点尝试。D 返回的结果不会被本类改写。
     * </p>
     */
    public RankMoviePlanExecutionResult execute(RankMoviePlanExecutionRequest request) {
        RankMoviePlanExecutionRequest executionRequest = Objects.requireNonNull(request, "执行请求不能为空");
        RunnableNodeSelection selection = stateMachine.selectRunnableNodes(executionRequest.state());
        ExecutionPlanNode node = requireRunnableRankMoviePlanNode(selection, executionRequest.nodeId());
        ExecutionRunState runningState = stateMachine.startNode(selection.state(), node.nodeId());
        ToolContext context = createToolContext(executionRequest, node);

        RankMoviePlanCommand command;
        try {
            command = createCommand(node);
        } catch (DateTimeException | IllegalArgumentException exception) {
            ToolResult<FixedRecommendationResult> failureResult = invalidParameterResult(context);
            return new RankMoviePlanExecutionResult(
                    stateMachine.recordToolResult(runningState, node.nodeId(), failureResult), failureResult);
        }

        ToolResult<FixedRecommendationResult> toolResult = rankMoviePlanTool.execute(context, command);
        return new RankMoviePlanExecutionResult(
                stateMachine.recordToolResult(runningState, node.nodeId(), toolResult), toolResult);
    }

    private static ExecutionPlanNode requireRunnableRankMoviePlanNode(
            RunnableNodeSelection selection, String nodeId) {
        return selection.nodes().stream()
                .filter(node -> node.nodeId().equals(nodeId))
                .filter(node -> RankMoviePlanTool.TARGET_NAME.equals(node.targetName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("节点当前不可执行 rankMoviePlan: " + nodeId));
    }

    private static ToolContext createToolContext(
            RankMoviePlanExecutionRequest request, ExecutionPlanNode node) {
        long deadlineMs = Math.min(
                request.remainingDeadlineMs(), AgentToolDefinitions.RANK_MOVIE_PLAN_TIMEOUT.toMillis());
        return new ToolContext(
                request.runId(),
                node.nodeId(),
                RankMoviePlanTool.TARGET_NAME,
                declaredSlotReferences(node),
                deadlineMs,
                request.traceId(),
                null,
                null,
                node.slotSnapshot().version());
    }

    private static List<String> declaredSlotReferences(ExecutionPlanNode node) {
        return node.inputRefs().stream()
                .filter(Objects::nonNull)
                .filter(reference -> reference.source() == InputReferenceSource.SLOT)
                .filter(reference -> reference.sourceId() != null && !reference.sourceId().isBlank())
                .map(reference -> "slots." + reference.sourceId())
                .toList();
    }

    private static RankMoviePlanCommand createCommand(ExecutionPlanNode node) {
        Map<String, InputReference> references = indexSlotReferences(node);
        Map<String, String> slotValues = node.slotSnapshot().values();
        String movieId = requiredSlotValue(references, slotValues, "movieId");
        String cinemaId = requiredSlotValue(references, slotValues, "cinemaId");
        LocalDate date = LocalDate.parse(requiredSlotValue(references, slotValues, "date"));
        LocalTime timeFrom = optionalTimeSlotValue(references, slotValues, "timeFrom");
        LocalTime timeTo = optionalTimeSlotValue(references, slotValues, "timeTo");
        return new RankMoviePlanCommand(movieId, cinemaId, date, timeFrom, timeTo);
    }

    private static Map<String, InputReference> indexSlotReferences(ExecutionPlanNode node) {
        if (node.slotSnapshot() == null) {
            throw new IllegalArgumentException("rankMoviePlan 节点缺少槽位快照");
        }
        Map<String, InputReference> references = new LinkedHashMap<>();
        for (InputReference reference : node.inputRefs()) {
            if (reference == null || !COMMAND_INPUT_NAMES.contains(reference.inputName())) {
                throw new IllegalArgumentException("rankMoviePlan 包含未知输入引用");
            }
            if (reference.source() != InputReferenceSource.SLOT
                    || reference.sourceId() == null
                    || reference.sourceId().isBlank()) {
                throw new IllegalArgumentException("rankMoviePlan 输入必须来自有效槽位");
            }
            if (references.putIfAbsent(reference.inputName(), reference) != null) {
                throw new IllegalArgumentException("rankMoviePlan 输入引用不能重复");
            }
        }
        return references;
    }

    private static String requiredSlotValue(
            Map<String, InputReference> references, Map<String, String> slotValues, String inputName) {
        InputReference reference = references.get(inputName);
        if (reference == null) {
            throw new IllegalArgumentException("rankMoviePlan 缺少必填输入: " + inputName);
        }
        String value = slotValues.get(reference.sourceId());
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("rankMoviePlan 槽位值不能为空: " + inputName);
        }
        return value;
    }

    private static LocalTime optionalTimeSlotValue(
            Map<String, InputReference> references, Map<String, String> slotValues, String inputName) {
        InputReference reference = references.get(inputName);
        if (reference == null) {
            return null;
        }
        String value = slotValues.get(reference.sourceId());
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("rankMoviePlan 槽位值不能为空: " + inputName);
        }
        return LocalTime.parse(value);
    }

    private static ToolResult<FixedRecommendationResult> invalidParameterResult(ToolContext context) {
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
