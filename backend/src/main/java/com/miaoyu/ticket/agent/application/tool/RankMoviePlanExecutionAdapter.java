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
 *
 * <p>这里不提供“按工具名执行”的通用入口。目标工具、Command 和结果类型都通过构造器与 Java 类型固定，
 * 因此模型计划中的字符串不能被解释成 Bean 名、反射类名或其他跨模块调用。
 */
public final class RankMoviePlanExecutionAdapter {
    /**
     * D 当前公开 Command 允许的全部输入名。
     *
     * <p>集合同时限制必填和可选槽位引用。新增 D 字段时必须由 D 确认 Command 变更，并同步修改 B 的
     * 工具定义、计划校验、转换逻辑和测试，不能因为模型生成了字段就透传。
     */
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
     *
     * <p>该方法不捕获 D 工具内部异常；工具实现应把预期业务失败映射为 ToolResult。未知异常需要由外层
     * 统一异常处理和 traceId 日志处理，不能在适配器里伪装成无场次或成功降级。
     */
    public RankMoviePlanExecutionResult execute(RankMoviePlanExecutionRequest request) {
        RankMoviePlanExecutionRequest executionRequest = Objects.requireNonNull(request, "执行请求不能为空");
        // 重新选择可运行节点，防止调用方使用旧 selection 或手工构造的 nodeId 绕过依赖与只读限制。
        RunnableNodeSelection selection = stateMachine.selectRunnableNodes(executionRequest.state());
        ExecutionPlanNode node = requireRunnableRankMoviePlanNode(selection, executionRequest.nodeId());
        // 必须先切换到 RUNNING，再把 ToolResult 交还状态机，保证 attemptCount 和重试规则成对记录。
        ExecutionRunState runningState = stateMachine.startNode(selection.state(), node.nodeId());
        ToolContext context = createToolContext(executionRequest, node);

        RankMoviePlanCommand command;
        try {
            command = createCommand(node);
        } catch (DateTimeException | IllegalArgumentException exception) {
            // 槽位文本不能转换为 D 的强类型 Command 时绝不访问 D，避免无效日期/时间触发查询或产生误导结果。
            ToolResult<FixedRecommendationResult> failureResult = invalidParameterResult(context);
            return new RankMoviePlanExecutionResult(
                    stateMachine.recordToolResult(runningState, node.nodeId(), failureResult), failureResult);
        }

        // 唯一允许的跨模块调用：公开的 RankMoviePlanTool.execute(context, command)。
        ToolResult<FixedRecommendationResult> toolResult = rankMoviePlanTool.execute(context, command);
        // 状态机决定 SUCCESS、PROCESSING、FAILED 与一次重试的状态语义；适配器不擅自修改结果状态。
        return new RankMoviePlanExecutionResult(
                stateMachine.recordToolResult(runningState, node.nodeId(), toolResult), toolResult);
    }

    private static ExecutionPlanNode requireRunnableRankMoviePlanNode(
            RunnableNodeSelection selection, String nodeId) {
        // nodeId 必须同时出现在状态机本轮 selection 中并明确指向该工具，二者缺一不可。
        return selection.nodes().stream()
                .filter(node -> node.nodeId().equals(nodeId))
                .filter(node -> RankMoviePlanTool.TARGET_NAME.equals(node.targetName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("节点当前不可执行 rankMoviePlan: " + nodeId));
    }

    private static ToolContext createToolContext(
            RankMoviePlanExecutionRequest request, ExecutionPlanNode node) {
        // 下游预算只能缩小：取调用方剩余时间与工具固定上限的较小值，不能因适配器而延长请求生命周期。
        long deadlineMs = Math.min(
                request.remainingDeadlineMs(), AgentToolDefinitions.RANK_MOVIE_PLAN_TIMEOUT.toMillis());
        // Context 只传运行关联、允许引用槽位和预算；不传用户身份、会话、模型原文或完整计划。
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
        // D 只需知道使用了哪些槽位，不需要收到槽位的全部值或与本工具无关的字段。
        return node.inputRefs().stream()
                .filter(Objects::nonNull)
                .filter(reference -> reference.source() == InputReferenceSource.SLOT)
                .filter(reference -> reference.sourceId() != null && !reference.sourceId().isBlank())
                .map(reference -> "slots." + reference.sourceId())
                .toList();
    }

    private static RankMoviePlanCommand createCommand(ExecutionPlanNode node) {
        // 所有值先从执行计划固定的槽位快照读取，不从当前会话或模型文本读取，保证计划校验后的输入不漂移。
        Map<String, InputReference> references = indexSlotReferences(node);
        Map<String, String> slotValues = node.slotSnapshot().values();
        String movieId = requiredSlotValue(references, slotValues, "movieId");
        String cinemaId = requiredSlotValue(references, slotValues, "cinemaId");
        // 日期和时间在 B 边界解析为 Java 时间类型；D 不必接收未经校验的字符串并重复猜测格式。
        LocalDate date = LocalDate.parse(requiredSlotValue(references, slotValues, "date"));
        LocalTime timeFrom = optionalTimeSlotValue(references, slotValues, "timeFrom");
        LocalTime timeTo = optionalTimeSlotValue(references, slotValues, "timeTo");
        return new RankMoviePlanCommand(movieId, cinemaId, date, timeFrom, timeTo);
    }

    private static Map<String, InputReference> indexSlotReferences(ExecutionPlanNode node) {
        if (node.slotSnapshot() == null) {
            // 快照缺失意味着计划未经过正确转换；不能回退到模型输入或全局可变槽位表。
            throw new IllegalArgumentException("rankMoviePlan 节点缺少槽位快照");
        }
        Map<String, InputReference> references = new LinkedHashMap<>();
        for (InputReference reference : node.inputRefs()) {
            if (reference == null || !COMMAND_INPUT_NAMES.contains(reference.inputName())) {
                // 未知字段可能是模型注入或 D 契约尚未同步的变化，统一按参数不合法结束节点。
                throw new IllegalArgumentException("rankMoviePlan 包含未知输入引用");
            }
            if (reference.source() != InputReferenceSource.SLOT
                    || reference.sourceId() == null
                    || reference.sourceId().isBlank()) {
                // 当前 Command 只接受服务端确认槽位，不能引用模型常量或其他节点的未约定结果。
                throw new IllegalArgumentException("rankMoviePlan 输入必须来自有效槽位");
            }
            if (references.putIfAbsent(reference.inputName(), reference) != null) {
                // 同一 Command 字段有多个来源会造成取值歧义，因此在调用 D 前拒绝。
                throw new IllegalArgumentException("rankMoviePlan 输入引用不能重复");
            }
        }
        return references;
    }

    private static String requiredSlotValue(
            Map<String, InputReference> references, Map<String, String> slotValues, String inputName) {
        InputReference reference = references.get(inputName);
        if (reference == null) {
            // 缺少引用属于已校验计划被破坏或适配器调用错误，不能用默认电影、影院或日期代替。
            throw new IllegalArgumentException("rankMoviePlan 缺少必填输入: " + inputName);
        }
        String value = slotValues.get(reference.sourceId());
        if (value == null || value.isBlank()) {
            // 引用存在但值为空说明确认槽位不完整，返回参数错误而不是把空串交给 D 的查询实现。
            throw new IllegalArgumentException("rankMoviePlan 槽位值不能为空: " + inputName);
        }
        return value;
    }

    private static LocalTime optionalTimeSlotValue(
            Map<String, InputReference> references, Map<String, String> slotValues, String inputName) {
        InputReference reference = references.get(inputName);
        if (reference == null) {
            // 时间窗两个字段都是可选约束；缺少引用时保持 null，由 D 按当前公开规则处理。
            return null;
        }
        String value = slotValues.get(reference.sourceId());
        if (value == null || value.isBlank()) {
            // 已声明的可选字段一旦引用，就必须有完整值，避免半填时间窗改变 D 查询语义。
            throw new IllegalArgumentException("rankMoviePlan 槽位值不能为空: " + inputName);
        }
        // 格式异常由 execute 的统一参数错误分支处理，确保不会触发 D 工具。
        return LocalTime.parse(value);
    }

    private static ToolResult<FixedRecommendationResult> invalidParameterResult(ToolContext context) {
        // 统一使用稳定通用参数错误码，不暴露具体槽位值或 Java 解析异常给模型和用户。
        return new ToolResult<>(
                ToolStatus.FAILED,
                null,
                CommonErrorCode.INVALID_PARAMETER.code(),
                // 参数错误不是临时故障，状态机不能对它自动重试。
                false,
                false,
                // 给上层结构化回复的安全提示，不携带 DateTimeException 的原始 message。
                "CHECK_INPUT",
                false,
                null,
                // 仍记录使用的槽位版本，便于按 traceId 审计到底是哪个确认快照产生了错误。
                context.stateVersion(),
                null,
                null);
    }
}
