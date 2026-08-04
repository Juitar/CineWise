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
import com.miaoyu.ticket.agent.application.reply.RecommendationReplyFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationReplyFactsMapper;
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
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 串起候选计划、服务端校验、只读推荐执行和结构化回复的最小主控。 */
public final class MinimalReadOnlyAgentService {
    private static final String NO_RUNNABLE_NODE = "NO_RUNNABLE_NODE";
    private static final String EXECUTION_LIMIT_REACHED = "EXECUTION_LIMIT_REACHED";
    private static final String UNSUPPORTED_NODE_TYPE = "UNSUPPORTED_NODE_TYPE";

    private final ModelGateway modelGateway;
    private final ToolRegistry toolRegistry;
    private final PlanSchemaValidator planSchemaValidator;
    private final ExecutionPlanStateMachine stateMachine;
    private final RankMoviePlanExecutionAdapter rankMoviePlanExecutionAdapter;

    public MinimalReadOnlyAgentService(
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            PlanSchemaValidator planSchemaValidator,
            ExecutionPlanStateMachine stateMachine,
            RankMoviePlanExecutionAdapter rankMoviePlanExecutionAdapter) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway 不能为空");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry 不能为空");
        this.planSchemaValidator = Objects.requireNonNull(planSchemaValidator, "planSchemaValidator 不能为空");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine 不能为空");
        this.rankMoviePlanExecutionAdapter = Objects.requireNonNull(
                rankMoviePlanExecutionAdapter, "rankMoviePlanExecutionAdapter 不能为空");
    }

    /** 执行一次同步、内存、只读请求；模型提出的计划必须再次通过服务端校验。 */
    public MinimalReadOnlyAgentResult run(MinimalReadOnlyAgentRequest request) {
        MinimalReadOnlyAgentRequest agentRequest = Objects.requireNonNull(request, "request 不能为空");
        Map<String, String> confirmedSlots = agentRequest.validationContext().slotSnapshot().values();
        PlanGenerationResponse generated = modelGateway.generatePlan(new PlanGenerationRequest(
                agentRequest.clientRequestId(),
                agentRequest.input(),
                confirmedSlots,
                allowedReadOnlyToolNames()));
        CandidatePlan candidatePlan = generated.candidatePlan();
        PlanValidationResult validation = planSchemaValidator.validate(
                candidatePlan, agentRequest.validationContext());
        if (!validation.isValid()) {
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

        ExecutionRunState state = stateMachine.initialize(validation.executionPlan().orElseThrow());
        List<ToolResult<FixedRecommendationResult>> toolResults = new ArrayList<>();
        ReplyGenerationResponse reply = null;
        int limit = Math.max(1, state.plan().nodes().size() * 2);
        for (int handled = 0; handled < limit; handled++) {
            RunnableNodeSelection selection = stateMachine.selectRunnableNodes(state);
            state = selection.state();
            if (selection.nodes().isEmpty()) {
                return finishWithoutRunnableNode(
                        agentRequest, candidatePlan, validation, state, toolResults, reply);
            }
            ExecutionPlanNode node = selection.nodes().getFirst();
            if (node.type() == PlanNodeType.CALL_TOOL
                    && RankMoviePlanTool.TARGET_NAME.equals(node.targetName())) {
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
                    ProgressReplyFacts facts = new ProgressReplyFacts(node.nodeId());
                    reply = generateReply(agentRequest, AgentReplyMessageType.PROGRESS, facts);
                    return result(candidatePlan, validation, state, toolResults, reply);
                }
                continue;
            }
            if (node.type() == PlanNodeType.ASK_USER) {
                String missingSlot = firstMissingRequiredSlot(confirmedSlots);
                if (missingSlot == null) {
                    return unsupportedNodeResult(
                            agentRequest, candidatePlan, validation, state, toolResults);
                }
                state = stateMachine.startNode(state, node.nodeId());
                QuestionReplyFacts facts = new QuestionReplyFacts(missingSlot);
                reply = generateReply(agentRequest, AgentReplyMessageType.QUESTION, facts);
                state = stateMachine.succeedNode(state, node.nodeId());
                continue;
            }
            if (node.type() == PlanNodeType.RENDER_RESULT) {
                ToolResult<FixedRecommendationResult> result = lastSuccessfulResult(toolResults);
                if (result == null) {
                    return unsupportedNodeResult(
                            agentRequest, candidatePlan, validation, state, toolResults);
                }
                state = stateMachine.startNode(state, node.nodeId());
                RecommendationReplyFacts facts = RecommendationReplyFactsMapper.from(result);
                AgentReplyMessageType type = facts.purchaseEligible()
                        ? AgentReplyMessageType.PLAN_CARD
                        : AgentReplyMessageType.MOVIE_CARD;
                reply = generateReply(agentRequest, type, facts);
                state = stateMachine.succeedNode(state, node.nodeId());
                continue;
            }
            return unsupportedNodeResult(agentRequest, candidatePlan, validation, state, toolResults);
        }
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
            List<ToolResult<FixedRecommendationResult>> toolResults,
            ReplyGenerationResponse reply) {
        if (reply != null) {
            return result(candidatePlan, validation, state, toolResults, reply);
        }
        ToolResult<FixedRecommendationResult> lastResult = lastResult(toolResults);
        ErrorReplyFacts facts;
        if (lastResult != null && lastResult.status() == ToolStatus.FAILED) {
            facts = lastResult.errorCode() == null
                    ? new ErrorReplyFacts(null, List.of("TOOL_FAILED"))
                    : new ErrorReplyFacts(lastResult.errorCode(), List.of());
        } else {
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
            List<ToolResult<FixedRecommendationResult>> toolResults) {
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
        return modelGateway.generateReply(new ReplyGenerationRequest(
                request.clientRequestId(),
                request.input(),
                type,
                payload));
    }

    private Set<String> allowedReadOnlyToolNames() {
        return toolRegistry.definitions().values().stream()
                .filter(ToolDefinition::readOnly)
                .map(ToolDefinition::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String firstMissingRequiredSlot(Map<String, String> confirmedSlots) {
        return toolRegistry.find(RankMoviePlanTool.TARGET_NAME)
                .stream()
                .flatMap(definition -> definition.requiredInputs().stream())
                .map(input -> input.name())
                .filter(name -> !hasText(confirmedSlots.get(name)))
                .findFirst()
                .orElse(null);
    }

    private static ToolResult<FixedRecommendationResult> lastSuccessfulResult(
            List<ToolResult<FixedRecommendationResult>> results) {
        for (int index = results.size() - 1; index >= 0; index--) {
            ToolResult<FixedRecommendationResult> result = results.get(index);
            if (result.status() == ToolStatus.SUCCESS && result.data() != null) {
                return result;
            }
        }
        return null;
    }

    private static ToolResult<FixedRecommendationResult> lastResult(
            List<ToolResult<FixedRecommendationResult>> results) {
        return results.isEmpty() ? null : results.getLast();
    }

    private static MinimalReadOnlyAgentResult result(
            CandidatePlan candidatePlan,
            PlanValidationResult validation,
            ExecutionRunState state,
            List<ToolResult<FixedRecommendationResult>> toolResults,
            ReplyGenerationResponse reply) {
        return new MinimalReadOnlyAgentResult(candidatePlan, validation, state, toolResults, reply);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
