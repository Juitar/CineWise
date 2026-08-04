package com.miaoyu.ticket.agent.infrastructure.model;

import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.model.PlanGenerationRequest;
import com.miaoyu.ticket.agent.application.model.PlanGenerationResponse;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationRequest;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.reply.ErrorReplyFacts;
import com.miaoyu.ticket.agent.application.reply.ProgressReplyFacts;
import com.miaoyu.ticket.agent.application.reply.QuestionReplyFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationReplyFacts;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlanNode;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolInputDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 不访问网络和当前时间的固定场景模型实现，仅用于开发和测试。 */
public final class MockModelGateway implements ModelGateway {
    private static final String SCENARIO_VERSION = "mock-readonly-flow-v2";

    private final PlanSchemaValidator planSchemaValidator;
    private final ToolRegistry toolRegistry;

    public MockModelGateway(PlanSchemaValidator planSchemaValidator, ToolRegistry toolRegistry) {
        this.planSchemaValidator = Objects.requireNonNull(planSchemaValidator, "planSchemaValidator 不能为空");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry 不能为空");
    }

    @Override
    public PlanGenerationResponse generatePlan(PlanGenerationRequest request) {
        PlanGenerationRequest planRequest = Objects.requireNonNull(request, "request 不能为空");
        String fingerprint = fingerprint(
                planRequest.clientRequestId(),
                planRequest.input(),
                canonicalSlots(planRequest.confirmedSlots()),
                canonicalAllowedTools(planRequest));
        CandidatePlan candidatePlan = new CandidatePlan(
                "mock-" + fingerprint,
                1,
                createNodes(planRequest));
        return new PlanGenerationResponse(
                candidatePlan,
                planSchemaValidator.validate(candidatePlan, validationContext(planRequest)));
    }

    @Override
    public ReplyGenerationResponse generateReply(ReplyGenerationRequest request) {
        ReplyGenerationRequest replyRequest = Objects.requireNonNull(request, "request 不能为空");
        String text = switch (replyRequest.requestedType()) {
            case QUESTION -> questionText((QuestionReplyFacts) replyRequest.payload());
            case PLAN_CARD -> recommendationText((RecommendationReplyFacts) replyRequest.payload(), true);
            case MOVIE_CARD -> recommendationText((RecommendationReplyFacts) replyRequest.payload(), false);
            case PROGRESS -> progressText((ProgressReplyFacts) replyRequest.payload());
            case ERROR -> errorText((ErrorReplyFacts) replyRequest.payload());
        };
        return new ReplyGenerationResponse(text, replyRequest.requestedType(), replyRequest.payload());
    }

    private List<CandidatePlanNode> createNodes(PlanGenerationRequest request) {
        ToolDefinition definition = allowedRankMoviePlan(request);
        if (definition == null) {
            return List.of();
        }
        String missingInput = firstMissingRequiredInput(definition, request.confirmedSlots());
        if (missingInput != null) {
            return List.of(new CandidatePlanNode(
                    "ask-" + missingInput,
                    PlanNodeType.ASK_USER,
                    null,
                    List.of(),
                    List.of(),
                    FailurePolicy.FAIL));
        }
        List<InputReference> inputReferences = new ArrayList<>();
        definition.requiredInputs().forEach(input -> inputReferences.add(slotReference(input.name())));
        if (hasText(request.confirmedSlots().get("timeFrom"))
                && hasText(request.confirmedSlots().get("timeTo"))) {
            inputReferences.add(slotReference("timeFrom"));
            inputReferences.add(slotReference("timeTo"));
        }
        return List.of(
                new CandidatePlanNode(
                        "rank-movie-plan",
                        PlanNodeType.CALL_TOOL,
                        RankMoviePlanTool.TARGET_NAME,
                        inputReferences,
                        List.of(),
                        FailurePolicy.RETRY_ONCE),
                new CandidatePlanNode(
                        "render-result",
                        PlanNodeType.RENDER_RESULT,
                        null,
                        List.of(),
                        List.of("rank-movie-plan"),
                        FailurePolicy.FAIL));
    }

    private ToolDefinition allowedRankMoviePlan(PlanGenerationRequest request) {
        if (!request.allowedToolNames().contains(RankMoviePlanTool.TARGET_NAME)) {
            return null;
        }
        return toolRegistry.find(RankMoviePlanTool.TARGET_NAME)
                .filter(ToolDefinition::readOnly)
                .orElse(null);
    }

    private PlanValidationContext validationContext(PlanGenerationRequest request) {
        ToolDefinition definition = allowedRankMoviePlan(request);
        if (definition == null) {
            return new PlanValidationContext(
                    Map.of(), Map.of(), new SlotSnapshot(0L, request.confirmedSlots()));
        }
        Map<String, Class<?>> slotTypes = new LinkedHashMap<>();
        for (ToolInputDefinition input : definition.inputs()) {
            if (request.confirmedSlots().containsKey(input.name())) {
                slotTypes.put(input.name(), input.valueType());
            }
        }
        return new PlanValidationContext(
                slotTypes, Map.of(), new SlotSnapshot(0L, request.confirmedSlots()));
    }

    private static String firstMissingRequiredInput(
            ToolDefinition definition, Map<String, String> confirmedSlots) {
        return definition.requiredInputs().stream()
                .map(ToolInputDefinition::name)
                .filter(name -> !hasText(confirmedSlots.get(name)))
                .findFirst()
                .orElse(null);
    }

    private static InputReference slotReference(String name) {
        return new InputReference(name, InputReferenceSource.SLOT, name);
    }

    private static String questionText(QuestionReplyFacts facts) {
        return "请补充" + facts.missingSlot() + "。";
    }

    private static String recommendationText(RecommendationReplyFacts facts, boolean purchaseEligible) {
        if (purchaseEligible) {
            return "已找到 " + facts.candidates().size() + " 个可购场次。";
        }
        return "已找到影片候选，但当前条件下暂无可购场次。";
    }

    private static String progressText(ProgressReplyFacts facts) {
        return "推荐节点 " + facts.nodeId() + " 仍在处理中。";
    }

    private static String errorText(ErrorReplyFacts facts) {
        if (facts.errorCode() != null) {
            return "当前请求暂时无法完成，错误码 " + facts.errorCode() + "。";
        }
        return "当前请求未通过安全校验。";
    }

    private static String canonicalSlots(Map<String, String> slots) {
        return slots.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    private static String canonicalAllowedTools(PlanGenerationRequest request) {
        return request.allowedToolNames().stream().sorted().collect(Collectors.joining(","));
    }

    private static String fingerprint(String... values) {
        try {
            String source = SCENARIO_VERSION + "|" + String.join("|", values);
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                result.append(String.format("%02x", bytes[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java 运行环境缺少 SHA-256", exception);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
