package com.miaoyu.ticket.agent.application.run;

import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.model.AgentIntent;
import com.miaoyu.ticket.agent.application.model.IntentClassificationRequest;
import com.miaoyu.ticket.agent.application.model.PlanGenerationRequest;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationRequest;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.TextReplyFacts;
import com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter;
import com.miaoyu.ticket.agent.application.tool.AgentToolExecutor;
import com.miaoyu.ticket.agent.application.tool.AgentToolExecutorRegistry;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationResult;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.run.RunnableNodeSelection;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Locale;

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
    private static final String SAFE_GENERAL_CHAT = "GENERAL_CHAT";
    private static final String SAFE_QUESTION_PREFIX = "QUESTION:";
    /** 普通 Agent 会话只允许已确认的观影只读能力；出行任务号没有普通会话的可信来源。 */
    private static final Set<String> MOVIE_TOOL_NAMES = Set.of("rankMoviePlan", "queryAvailableDates", "queryShows");
    private static final String TRAVEL_TASK_ID = "travelTaskId";
    /** 仅这些展示字段可以要求用户补充；各种业务引用 ID 必须由服务端结果或上下文提供。 */
    private static final Set<String> QUESTIONABLE_INPUTS = Set.of("cityCode", "date", "ticketCount");

    private final ModelGateway modelGateway;
    private final ToolRegistry toolRegistry;
    private final PlanSchemaValidator planSchemaValidator;
    private final ExecutionPlanStateMachine stateMachine;
    private final AgentToolExecutorRegistry toolExecutorRegistry;
    private final ProfileContextPrefetcher profileContextPrefetcher;

    public MultiToolSupervisor(
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            PlanSchemaValidator planSchemaValidator,
            ExecutionPlanStateMachine stateMachine,
            List<? extends AgentToolExecutor<?, ?>> readOnlyAdapters) {
        this(modelGateway, toolRegistry, planSchemaValidator, stateMachine, readOnlyAdapters,
                ProfileContextPrefetcher.disabled());
    }

    public MultiToolSupervisor(ModelGateway modelGateway, ToolRegistry toolRegistry,
            PlanSchemaValidator planSchemaValidator,
            ExecutionPlanStateMachine stateMachine, List<? extends AgentToolExecutor<?, ?>> readOnlyAdapters,
            ProfileContextPrefetcher profileContextPrefetcher) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "模型网关不能为空");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "工具白名单不能为空");
        this.planSchemaValidator = Objects.requireNonNull(planSchemaValidator, "计划校验器不能为空");
        this.stateMachine = Objects.requireNonNull(stateMachine, "状态机不能为空");
        this.toolExecutorRegistry = new AgentToolExecutorRegistry(toolRegistry, readOnlyAdapters);
        this.profileContextPrefetcher = Objects.requireNonNull(profileContextPrefetcher, "画像预取器不能为空");
    }

    /** 生成、校验并执行本轮可运行的只读节点；写节点始终保留给既有确认动作服务。 */
    public MultiToolSupervisorResult run(MultiToolSupervisorRequest request) {
        MultiToolSupervisorRequest supervisorRequest = Objects.requireNonNull(request, "请求不能为空");
        AgentIntent intent = modelGateway.classifyIntent(new IntentClassificationRequest(supervisorRequest.input()));
        Set<String> allowedToolNames = allowedToolNames(intent, supervisorRequest.validationContext());
        if (allowedToolNames.isEmpty()) {
            // 普通会话没有可信 travelTaskId 来源；TRAVEL 也只能走安全文本，不能追问内部任务号。
            var emptyPlan = new com.miaoyu.ticket.agent.domain.plan.CandidatePlan(
                    UUID.randomUUID().toString(), 1, List.of());
            PlanValidationResult emptyValidation = planSchemaValidator.validate(
                    emptyPlan, supervisorRequest.validationContext());
            return new MultiToolSupervisorResult(emptyPlan, emptyValidation,
                    stateMachine.initialize(emptyValidation.executionPlan().orElseThrow()), List.of(), false,
                    SAFE_GENERAL_CHAT, generalChatReply(supervisorRequest));
        }
        var generated = modelGateway.generatePlan(new PlanGenerationRequest(
                supervisorRequest.clientRequestId(),
                supervisorRequest.input(),
                supervisorRequest.validationContext().slotSnapshot().values(),
                allowedToolNames,
                profileContextPrefetcher.prefetch(supervisorRequest)));
        PlanValidationResult validation = validateForAllowedTools(
                generated.candidatePlan(), supervisorRequest.validationContext(), allowedToolNames);
        if (!validation.isValid()) {
            return new MultiToolSupervisorResult(
                    generated.candidatePlan(), validation, null, List.of(), false, SAFE_PLAN_REJECTED);
        }
        var serverPlan = withServerPlanId(generated.candidatePlan());
        PlanValidationResult serverValidation = validateForAllowedTools(
                serverPlan, supervisorRequest.validationContext(), allowedToolNames);
        return execute(
                supervisorRequest,
                serverPlan,
                serverValidation,
                stateMachine.initialize(serverValidation.executionPlan().orElseThrow()),
                new ArrayList<>(), allowedToolNames);
    }

    /**
     * 执行一个已校验计划；只有只读工具明确建议重规划时才进入下一版计划。
     *
     * <p>写节点由状态机保持等待确认，因此这里没有任何路径会因为模型、断线或超时再次调用
     * {@code createOrder}。重规划仅复用原运行标识和请求标识，持久化层仍须以 CAS 接收返回快照。</p>
     */
    private MultiToolSupervisorResult execute(
            MultiToolSupervisorRequest request,
            com.miaoyu.ticket.agent.domain.plan.CandidatePlan candidatePlan,
            PlanValidationResult validation,
            ExecutionRunState initialState,
            List<MultiToolSupervisorResult.NodeToolResult> initialResults,
            Set<String> allowedToolNames) {
        ExecutionRunState state = initialState;
        List<MultiToolSupervisorResult.NodeToolResult> results = new ArrayList<>(initialResults);
        int limit = Math.max(1, state.plan().nodes().size() * 2);
        for (int handled = 0; handled < limit; handled++) {
            RunnableNodeSelection selection = stateMachine.selectRunnableNodes(state);
            state = selection.state();
            if (selection.nodes().isEmpty()) {
                return completed(candidatePlan, validation, state, results);
            }
            // 每轮从快照选择一个节点；下一轮重新计算可运行集合，避免使用旧 selection 重复执行。
            ExecutionPlanNode node = selection.nodes().getFirst();
            if (node.type() == com.miaoyu.ticket.agent.domain.plan.PlanNodeType.ASK_USER) {
                String missingInput = firstMissingRequiredInput(candidatePlan, request.validationContext(),
                        allowedToolNames);
                if (missingInput == null) {
                    return new MultiToolSupervisorResult(
                            candidatePlan, validation, state, results, false, SAFE_PLAN_REJECTED);
                }
                // 追问本身是本轮已完成的安全输出；用户补充信息后由提交入口创建下一版计划。
                state = stateMachine.succeedNode(stateMachine.startNode(state, node.nodeId()), node.nodeId());
                // ASK_USER 不能由模型自由指定字段；缺失字段由服务端白名单定义推导。
                return new MultiToolSupervisorResult(
                        candidatePlan, validation, state, results, false, SAFE_QUESTION_PREFIX + missingInput);
            }
            if (node.type() == com.miaoyu.ticket.agent.domain.plan.PlanNodeType.VALIDATE
                    || node.type() == com.miaoyu.ticket.agent.domain.plan.PlanNodeType.RENDER_RESULT) {
                // 计划校验已在进入状态机前完成；这些节点只记录安全的阶段推进，不调用业务模块。
                state = stateMachine.succeedNode(stateMachine.startNode(state, node.nodeId()), node.nodeId());
                continue;
            }
            ReadOnlyToolExecutionAdapter adapter = toolExecutorRegistry.require(node.targetName());
            ReadOnlyToolExecutionAdapter.ExecutionRequest executionRequest =
                    new ReadOnlyToolExecutionAdapter.ExecutionRequest(
                    state, node.nodeId(), request.runId(), request.traceId(), request.remainingDeadlineMs(),
                    request.distanceContextId(), request.distancePreference());
            ReadOnlyToolExecutionAdapter.ExecutionResult executed = adapter.execute(executionRequest);
            state = executed.state();
            results.add(new MultiToolSupervisorResult.NodeToolResult(
                    node.nodeId(), node.targetName(), executed.toolResult()));
            if (executed.toolResult().status() == ToolStatus.FAILED && executed.toolResult().replanSuggested()) {
                MultiToolSupervisorResult replanned = replan(request,
                        new MultiToolSupervisorResult(candidatePlan, validation, state, results, false, null),
                        allowedToolNames);
                if (replanned.validation().isValid() && replanned.state() != state) {
                    return execute(request, replanned.candidatePlan(), replanned.validation(), replanned.state(),
                            replanned.toolResults(), allowedToolNames);
                }
            }
            if (executed.toolResult().status() == ToolStatus.PROCESSING) {
                return new MultiToolSupervisorResult(
                        candidatePlan, validation, state, results, false, SAFE_PROCESSING);
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
        AgentIntent intent = modelGateway.classifyIntent(new IntentClassificationRequest(request.input()));
        return replan(request, previousResult, allowedToolNames(intent, request.validationContext()));
    }

    private MultiToolSupervisorResult replan(
            MultiToolSupervisorRequest request,
            MultiToolSupervisorResult previousResult,
            Set<String> allowedToolNames) {
        MultiToolSupervisorRequest supervisorRequest = Objects.requireNonNull(request, "请求不能为空");
        MultiToolSupervisorResult previous = Objects.requireNonNull(previousResult, "前一运行结果不能为空");
        if (previous.state() == null) {
            throw new IllegalStateException("未通过校验的计划不能重规划");
        }
        var generated = modelGateway.generatePlan(new PlanGenerationRequest(
                supervisorRequest.clientRequestId(), supervisorRequest.input(),
                supervisorRequest.validationContext().slotSnapshot().values(), allowedToolNames,
                profileContextPrefetcher.prefetch(supervisorRequest)));
        PlanValidationResult validation = validateForAllowedTools(
                generated.candidatePlan(), supervisorRequest.validationContext(), allowedToolNames);
        if (!validation.isValid()) {
            return new MultiToolSupervisorResult(
                    generated.candidatePlan(), validation, previous.state(), previous.toolResults(),
                    previous.awaitingConfirmation(), SAFE_PLAN_REJECTED);
        }
        var serverPlan = withServerPlanId(generated.candidatePlan());
        PlanValidationResult serverValidation = validateForAllowedTools(
                serverPlan, supervisorRequest.validationContext(), allowedToolNames);
        ExecutionRunState replanned = stateMachine.acceptReplan(
                previous.state(), serverValidation.executionPlan().orElseThrow());
        return new MultiToolSupervisorResult(
                serverPlan, serverValidation, replanned, previous.toolResults(), false, null);
    }

    private static com.miaoyu.ticket.agent.domain.plan.CandidatePlan withServerPlanId(
            com.miaoyu.ticket.agent.domain.plan.CandidatePlan plan) {
        return new com.miaoyu.ticket.agent.domain.plan.CandidatePlan(
                UUID.randomUUID().toString(), plan.version(), plan.nodes());
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

    private Set<String> allowedMovieToolNames() {
        return toolRegistry.definitions().keySet().stream()
                .filter(MOVIE_TOOL_NAMES::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse generalChatReply(
            MultiToolSupervisorRequest request) {
        try {
            var reply = modelGateway.generateReply(new ReplyGenerationRequest(
                    request.clientRequestId(), request.input(), AgentReplyMessageType.TEXT, new TextReplyFacts()));
            return isSafeGeneralText(reply.text()) ? reply : safeGeneralChatReply();
        } catch (RuntimeException exception) {
            // 供应商异常不能中断已创建的运行，也不能回显内部异常或改为调用 Tool。
            return safeGeneralChatReply();
        }
    }

    /** 普通文本不能携带内部标识或服务端路径；命中后必须完全改为固定安全文案。 */
    private static boolean isSafeGeneralText(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return Set.of(
                "traveltaskid", "userid", "runid", "actionid", "planid", "showid", "movieid",
                "cinemaid", "taskid", "orderid", "seatid", "ticketid", "refundid", "database id",
                "数据库id", "/api/", "http://", "https://", "controller", "repository", "mapper")
                .stream().noneMatch(normalized::contains);
    }

    private static com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse safeGeneralChatReply() {
        return new com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse(
                "我可以帮你找电影、推荐观影方案或查询场次。你想看什么类型的电影？",
                AgentReplyMessageType.TEXT, new TextReplyFacts());
    }

    private Set<String> allowedToolNames(
            AgentIntent intent, com.miaoyu.ticket.agent.domain.plan.PlanValidationContext context) {
        if (intent == null) {
            // 空结果属于不确定分类，不能因为注册表里存在 Tool 就扩大本轮可见范围。
            return Set.of();
        }
        if (intent == AgentIntent.MOVIE) {
            return allowedMovieToolNames();
        }
        if (intent == AgentIntent.TRAVEL && hasTrustedTravelTaskContext(context)) {
            return toolRegistry.find("getTravelAdvice").isPresent() ? Set.of("getTravelAdvice") : Set.of();
        }
        return Set.of();
    }

    private static boolean hasTrustedTravelTaskContext(
            com.miaoyu.ticket.agent.domain.plan.PlanValidationContext context) {
        String taskId = context.slotSnapshot().values().get(TRAVEL_TASK_ID);
        // 普通会话槽位服务不会写入该字段；只有已验证的服务端上下文才会同时声明类型和值。
        return taskId != null && !taskId.isBlank() && context.slotTypes().get(TRAVEL_TASK_ID) == String.class;
    }

    private PlanValidationResult validateForAllowedTools(
            com.miaoyu.ticket.agent.domain.plan.CandidatePlan candidatePlan,
            com.miaoyu.ticket.agent.domain.plan.PlanValidationContext context,
            Set<String> allowedToolNames) {
        boolean hasDisallowedTool = candidatePlan != null && candidatePlan.nodes().stream()
                .filter(node -> node.type() == com.miaoyu.ticket.agent.domain.plan.PlanNodeType.CALL_TOOL)
                .map(com.miaoyu.ticket.agent.domain.plan.CandidatePlanNode::targetName)
                .anyMatch(name -> !allowedToolNames.contains(name));
        if (hasDisallowedTool) {
            return PlanValidationResult.invalid(List.of(new com.miaoyu.ticket.agent.domain.plan.PlanValidationIssue(
                    com.miaoyu.ticket.agent.domain.plan.PlanValidationIssueCode.TOOL_NOT_FOUND,
                    null, "targetName", "工具不在本轮允许范围")));
        }
        return planSchemaValidator.validate(candidatePlan, context);
    }

    private String firstMissingRequiredInput(
            com.miaoyu.ticket.agent.domain.plan.CandidatePlan candidatePlan,
            com.miaoyu.ticket.agent.domain.plan.PlanValidationContext validationContext, Set<String> allowedTools) {
        return candidatePlan.nodes().stream()
                .filter(node -> node.type() == com.miaoyu.ticket.agent.domain.plan.PlanNodeType.ASK_USER)
                .map(com.miaoyu.ticket.agent.domain.plan.CandidatePlanNode::targetName)
                .filter(Objects::nonNull)
                .filter(allowedTools::contains)
                .map(toolRegistry::find)
                .flatMap(java.util.Optional::stream)
                .flatMap(definition -> definition.requiredInputs().stream())
                .map(com.miaoyu.ticket.agent.domain.tool.ToolInputDefinition::name)
                .filter(QUESTIONABLE_INPUTS::contains)
                .filter(name -> {
                    String value = validationContext.slotSnapshot().values().get(name);
                    return value == null || value.isBlank();
                })
                .findFirst()
                .orElse(null);
    }
}
