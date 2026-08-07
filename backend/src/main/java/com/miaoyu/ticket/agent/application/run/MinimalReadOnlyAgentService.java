package com.miaoyu.ticket.agent.application.run;

import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.model.PlanGenerationRequest;
import com.miaoyu.ticket.agent.application.model.PlanGenerationResponse;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationRequest;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.AgentReplyPayload;
import com.miaoyu.ticket.agent.application.reply.ErrorReplyFacts;
import com.miaoyu.ticket.agent.application.reply.ProgressReplyFacts;
import com.miaoyu.ticket.agent.application.reply.QuestionReplyFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardFactsMapper;
import com.miaoyu.ticket.agent.application.tool.RankMoviePlanExecutionAdapter;
import com.miaoyu.ticket.agent.application.tool.RankMoviePlanExecutionRequest;
import com.miaoyu.ticket.agent.application.tool.RankMoviePlanExecutionResult;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationIssue;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationResult;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.run.RunnableNodeSelection;
import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlanResult;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 串起候选计划、服务端校验、只读推荐执行和结构化回复的最小主控。
 *
 * <p>这里是 B 的应用层入口，而不是通用工具调度器。当前只接受已经登记的
 * {@code rankMoviePlan}，这样模型给出不存在的工具名时，不能借由本类访问 Spring Bean、
 * 反射目标或 D 的其他接口。
 *
 * <p>本类不保存会话、运行记录或工具结果。调用方以后接入会话或 SSE 时，仍要以外层持久化的
 * 运行数据为准；这一轮返回的 {@link ExecutionRunState} 只是本次同步调用的内存快照。
 */
public final class MinimalReadOnlyAgentService {
    /** 没有可运行节点且没有更具体工具错误时使用，避免把内部状态细节暴露给回复模型。 */
    private static final String NO_RUNNABLE_NODE = "NO_RUNNABLE_NODE";
    /** 防止错误计划或状态机实现问题造成无限循环；达到上限后只能安全结束本轮请求。 */
    private static final String EXECUTION_LIMIT_REACHED = "EXECUTION_LIMIT_REACHED";
    /** 当前最小流程不猜测未知节点的用途，必须明确返回错误而不能把节点当成成功跳过。 */
    private static final String UNSUPPORTED_NODE_TYPE = "UNSUPPORTED_NODE_TYPE";

    private final ModelGateway modelGateway;
    private final ToolRegistry toolRegistry;
    private final PlanSchemaValidator planSchemaValidator;
    private final ExecutionPlanStateMachine stateMachine;
    private final RankMoviePlanExecutionAdapter rankMoviePlanExecutionAdapter;
    private final Clock clock;

    public MinimalReadOnlyAgentService(
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            PlanSchemaValidator planSchemaValidator,
            ExecutionPlanStateMachine stateMachine,
            RankMoviePlanExecutionAdapter rankMoviePlanExecutionAdapter,
            Clock clock) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway 不能为空");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry 不能为空");
        this.planSchemaValidator = Objects.requireNonNull(planSchemaValidator, "planSchemaValidator 不能为空");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine 不能为空");
        this.rankMoviePlanExecutionAdapter = Objects.requireNonNull(
                rankMoviePlanExecutionAdapter, "rankMoviePlanExecutionAdapter 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 执行一次同步、内存、只读请求；模型提出的计划必须再次通过服务端校验。
     *
     * <p>模型只负责产生候选计划，不能决定工具、依赖关系或槽位引用是否可信。即使开发环境的
     * Mock 已在生成时做过校验，这里也必须重新使用调用方提供的 {@code validationContext} 校验，
     * 因为真实模型、重放请求和后续模型实现都不在 Mock 的可信边界内。
     *
     * <p>本方法只执行只读工具。它不会创建确认动作、写入状态、发布 SSE，也不会把工具结果当作
     * 订单、座位或支付事实；这些能力必须由后续独立 change 在各自入口实现。
     */
    public MinimalReadOnlyAgentResult run(MinimalReadOnlyAgentRequest request) {
        MinimalReadOnlyAgentRequest agentRequest = Objects.requireNonNull(request, "request 不能为空");
        // 槽位快照来自服务端校验上下文，不从模型计划的 inputReferences 反向取值，防止模型伪造输入。
        Map<String, String> confirmedSlots = agentRequest.validationContext().slotSnapshot().values();
        // 传给模型的工具名单由注册表计算；客户端输入不能扩大这个名单。
        PlanGenerationResponse generated = modelGateway.generatePlan(new PlanGenerationRequest(
                agentRequest.clientRequestId(),
                agentRequest.input(),
                confirmedSlots,
                allowedReadOnlyToolNames()));
        CandidatePlan candidatePlan = generated.candidatePlan();
        PlanValidationResult validation = planSchemaValidator.validate(
                candidatePlan, agentRequest.validationContext());
        if (!validation.isValid()) {
            // 校验问题只转换成稳定问题码，不把模型原始输出、校验细节或槽位原文交给回复生成器。
            ErrorReplyFacts facts = new ErrorReplyFacts(
                    null,
                    validation.issues().stream()
                            .map(PlanValidationIssue::code)
                            .map(Enum::name)
                            .distinct()
                            .toList());
            return new MinimalReadOnlyAgentResult(
                    candidatePlan,
                    validation,
                    null,
                    List.of(),
                    generateReply(agentRequest, AgentReplyMessageType.ERROR, facts));
        }

        // 状态机只负责节点选择和状态推进；真实工具调用始终留在应用层适配器中。
        ExecutionRunState state = stateMachine.initialize(validation.executionPlan().orElseThrow());
        List<ToolResult<?>> toolResults = new ArrayList<>();
        ReplyGenerationResponse reply = null;
        // 每个节点理论上至多被选择两次（首次与一次重试），上限同时防御异常依赖图。
        int limit = Math.max(1, state.plan().nodes().size() * 2);
        for (int handled = 0; handled < limit; handled++) {
            RunnableNodeSelection selection = stateMachine.selectRunnableNodes(state);
            state = selection.state();
            if (selection.nodes().isEmpty()) {
                // 所有节点结束后统一在此决定是保留已生成回复，还是转为可公开的失败事实。
                return finishWithoutRunnableNode(
                        agentRequest, candidatePlan, validation, state, toolResults, reply);
            }
            ExecutionPlanNode node = selection.nodes().getFirst();
            if (node.type() == PlanNodeType.CALL_TOOL
                    && RankMoviePlanTool.TARGET_NAME.equals(node.targetName())) {
                // 不根据 targetName 动态查找 Bean；适配器的构造器已经把唯一允许的 D 工具固定为具体类型。
                RankMoviePlanExecutionResult execution = rankMoviePlanExecutionAdapter.execute(
                        new RankMoviePlanExecutionRequest(
                                state,
                                node.nodeId(),
                                agentRequest.runId(),
                                agentRequest.traceId(),
                                agentRequest.remainingDeadlineMs()));
                state = execution.state();
                toolResults.add(execution.toolResult());
                if (execution.toolResult().status() == ToolStatus.PROCESSING) {
                    // 结果未知时不能继续渲染旧数据，也不能重试只读调用来伪造完成结果；本轮到此结束。
                    ProgressReplyFacts facts = new ProgressReplyFacts(node.nodeId());
                    reply = generateReply(agentRequest, AgentReplyMessageType.PROGRESS, facts);
                    return result(candidatePlan, validation, state, toolResults, reply);
                }
                continue;
            }
            if (node.type() == PlanNodeType.ASK_USER) {
                // 追问字段从白名单工具的必填输入推导，不信任节点自身携带的任意字段名。
                String missingSlot = firstMissingRequiredSlot(confirmedSlots);
                if (missingSlot == null) {
                    // 如果计划要求追问但服务端认为槽位齐全，说明节点类型和当前规则不一致，不能臆测用户问题。
                    return unsupportedNodeResult(
                            agentRequest, candidatePlan, validation, state, toolResults);
                }
                state = stateMachine.startNode(state, node.nodeId());
                // 一轮只产生一个缺失字段问题，避免同时追问多项导致用户回复无法可靠写回槽位。
                QuestionReplyFacts facts = new QuestionReplyFacts(missingSlot);
                reply = generateReply(agentRequest, AgentReplyMessageType.QUESTION, facts);
                state = stateMachine.succeedNode(state, node.nodeId());
                continue;
            }
            if (node.type() == PlanNodeType.RENDER_RESULT) {
                // 渲染只能使用本轮最后一个成功且有数据的结果，FAILED 和 PROCESSING 都不能被当成推荐内容。
                ToolResult<RecommendationPlanResult> result = lastSuccessfulResult(toolResults);
                if (result == null) {
                    return unsupportedNodeResult(
                            agentRequest, candidatePlan, validation, state, toolResults);
                }
                state = stateMachine.startNode(state, node.nodeId());
                RecommendationPlanCardFacts facts = RecommendationPlanCardFactsMapper.from(result, clock.instant());
                // 空方案与放宽建议同样是 D 的完整推荐结果，统一用 PLAN_CARD 表示，不能降级成旧影片候选。
                reply = generateReply(agentRequest, AgentReplyMessageType.PLAN_CARD, facts);
                state = stateMachine.succeedNode(state, node.nodeId());
                continue;
            }
            // 新节点类型必须先补齐主控语义和测试，不能因默认分支而静默跳过。
            return unsupportedNodeResult(agentRequest, candidatePlan, validation, state, toolResults);
        }
        // 即使所有依赖都看似可运行，达到防循环上限后也只能返回稳定错误，不能继续占用请求线程。
        ErrorReplyFacts facts = new ErrorReplyFacts(null, List.of(EXECUTION_LIMIT_REACHED));
        return result(
                candidatePlan,
                validation,
                state,
                toolResults,
                generateReply(agentRequest, AgentReplyMessageType.ERROR, facts));
    }

    private MinimalReadOnlyAgentResult finishWithoutRunnableNode(
            MinimalReadOnlyAgentRequest request,
            CandidatePlan candidatePlan,
            PlanValidationResult validation,
            ExecutionRunState state,
            List<ToolResult<?>> toolResults,
            ReplyGenerationResponse reply) {
        if (reply != null) {
            // 已产生 QUESTION 或推荐卡后，状态机没有后续节点是正常结束，不应覆盖用户可见回复。
            return result(candidatePlan, validation, state, toolResults, reply);
        }
        ToolResult<RecommendationPlanResult> lastResult = lastResult(toolResults);
        ErrorReplyFacts facts;
        if (lastResult != null && lastResult.status() == ToolStatus.FAILED) {
            // 工具适配器已经把异常收敛为稳定错误码；这里不拼接 exception message，避免泄露下游细节。
            facts = lastResult.errorCode() == null
                    ? new ErrorReplyFacts(null, List.of("TOOL_FAILED"))
                    : new ErrorReplyFacts(lastResult.errorCode(), List.of());
        } else {
            // 计划校验已通过却无法再选出节点时，返回通用状态错误，供调用方记录 traceId 后排查。
            facts = new ErrorReplyFacts(null, List.of(NO_RUNNABLE_NODE));
        }
        return result(
                candidatePlan,
                validation,
                state,
                toolResults,
                generateReply(request, AgentReplyMessageType.ERROR, facts));
    }

    private MinimalReadOnlyAgentResult unsupportedNodeResult(
            MinimalReadOnlyAgentRequest request,
            CandidatePlan candidatePlan,
            PlanValidationResult validation,
            ExecutionRunState state,
            List<ToolResult<?>> toolResults) {
        // 未支持节点不调用状态机的成功/失败推进，避免人为修改计划状态掩盖主控能力缺口。
        ErrorReplyFacts facts = new ErrorReplyFacts(null, List.of(UNSUPPORTED_NODE_TYPE));
        return result(
                candidatePlan,
                validation,
                state,
                toolResults,
                generateReply(request, AgentReplyMessageType.ERROR, facts));
    }

    private ReplyGenerationResponse generateReply(
            MinimalReadOnlyAgentRequest request,
            AgentReplyMessageType type,
            AgentReplyPayload payload) {
        // 回复模型只拿经过本类和 Mapper 收窄后的事实，绝不接触原始工具结果、异常对象或完整运行上下文。
        return modelGateway.generateReply(new ReplyGenerationRequest(
                request.clientRequestId(),
                request.input(),
                type,
                payload));
    }

    private Set<String> allowedReadOnlyToolNames() {
        // 白名单在服务端从注册定义派生；即使未来模型请求更多工具，也不会改变本次生成请求的权限。
        return toolRegistry.definitions().values().stream()
                .filter(ToolDefinition::readOnly)
                .map(ToolDefinition::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String firstMissingRequiredSlot(Map<String, String> confirmedSlots) {
        // 当前只允许一个工具，因此追问顺序与 ToolDefinition.requiredInputs 的声明顺序一致，便于测试和前端展示。
        return toolRegistry.find(RankMoviePlanTool.TARGET_NAME)
                .stream()
                .flatMap(definition -> definition.requiredInputs().stream())
                .map(input -> input.name())
                .filter(name -> !hasText(confirmedSlots.get(name)))
                .findFirst()
                .orElse(null);
    }

    private static ToolResult<RecommendationPlanResult> lastSuccessfulResult(
            List<ToolResult<?>> results) {
        for (int index = results.size() - 1; index >= 0; index--) {
            ToolResult<?> result = results.get(index);
            if (result.status() == ToolStatus.SUCCESS && result.data() instanceof RecommendationPlanResult) {
                // 重试后优先使用最新成功结果，避免把首次调用的过期结果渲染给用户。
                @SuppressWarnings("unchecked")
                ToolResult<RecommendationPlanResult> recommendation = (ToolResult<RecommendationPlanResult>) result;
                return recommendation;
            }
        }
        return null;
    }

    private static ToolResult<RecommendationPlanResult> lastResult(
            List<ToolResult<?>> results) {
        // 失败映射要看实际最后一次执行，不能因为更早成功就掩盖后续重试失败。
        if (results.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        ToolResult<RecommendationPlanResult> result = (ToolResult<RecommendationPlanResult>) results.getLast();
        return result;
    }

    private static MinimalReadOnlyAgentResult result(
            CandidatePlan candidatePlan,
            PlanValidationResult validation,
            ExecutionRunState state,
            List<ToolResult<?>> toolResults,
            ReplyGenerationResponse reply) {
        return new MinimalReadOnlyAgentResult(candidatePlan, validation, state, toolResults, reply);
    }

    private static boolean hasText(String value) {
        // 空白文本与未提供槽位等价；否则用户只输入空格会绕过必填槽位追问。
        return value != null && !value.isBlank();
    }
}
