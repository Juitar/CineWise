package com.miaoyu.ticket.agent.infrastructure.model;

import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.model.PlanGenerationRequest;
import com.miaoyu.ticket.agent.application.model.PlanGenerationResponse;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationRequest;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.reply.ErrorReplyFacts;
import com.miaoyu.ticket.agent.application.reply.ProgressReplyFacts;
import com.miaoyu.ticket.agent.application.reply.QuestionReplyFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationReplyFacts;
import com.miaoyu.ticket.agent.application.reply.TravelAdviceCardFacts;
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

/**
 * 不访问网络和当前时间的固定场景模型实现，仅用于开发和测试。
 *
 * <p>它不是生产模型降级方案。生产环境的模型故障必须由真正的模型适配器按超时和错误规则处理，
 * 不能悄悄把用户请求改成这套固定计划，否则用户会误以为得到了真实的智能决策。
 *
 * <p>Mock 对相同请求给出相同计划 ID 和节点结构，以便单元测试验证状态机、工具调用和回复类型，
 * 而不依赖网络、当前日期或随机数。
 */
public final class MockModelGateway implements ModelGateway {
    /** 指纹版本变更代表 Mock 场景语义变更，避免不同场景的同一输入复用相同计划 ID。 */
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
        // 指纹包含确认槽位和允许工具；它们不同意味着模型可见事实或可执行范围不同，不能复用计划标识。
        String fingerprint = fingerprint(
                planRequest.clientRequestId(),
                planRequest.input(),
                canonicalSlots(planRequest.confirmedSlots()),
                canonicalAllowedTools(planRequest));
        CandidatePlan candidatePlan = new CandidatePlan(
                "mock-" + fingerprint,
                1,
                createNodes(planRequest));
        // 这里的校验仅用于 Mock 自检；主控仍会使用实际调用的 validationContext 再次校验一次。
        return new PlanGenerationResponse(
                candidatePlan,
                planSchemaValidator.validate(candidatePlan, validationContext(planRequest)));
    }

    @Override
    public ReplyGenerationResponse generateReply(ReplyGenerationRequest request) {
        ReplyGenerationRequest replyRequest = Objects.requireNonNull(request, "request 不能为空");
        // switch 只接受已由 ReplyGenerationRequest 校验过的类型和事实，Mock 不从自由文本推断业务状态。
        String text = switch (replyRequest.requestedType()) {
            case QUESTION -> questionText((QuestionReplyFacts) replyRequest.payload());
            case PLAN_CARD -> recommendationPlanText((RecommendationPlanCardFacts) replyRequest.payload());
            case TRAVEL_ADVICE_CARD -> travelAdviceText((TravelAdviceCardFacts) replyRequest.payload());
            case MOVIE_CARD -> recommendationText((RecommendationReplyFacts) replyRequest.payload(), false);
            case SELECT_SEATS -> "请在选座页面选择座位。";
            case PROGRESS -> progressText((ProgressReplyFacts) replyRequest.payload());
            case ERROR -> errorText((ErrorReplyFacts) replyRequest.payload());
        };
        return new ReplyGenerationResponse(text, replyRequest.requestedType(), replyRequest.payload());
    }

    private List<CandidatePlanNode> createNodes(PlanGenerationRequest request) {
        ToolDefinition definition = allowedRankMoviePlan(request);
        if (definition == null) {
            // 白名单未授予工具时宁可生成空计划，让服务端校验失败；不能创建任意默认工具节点。
            return List.of();
        }
        String missingInput = firstMissingRequiredInput(definition, request.confirmedSlots());
        if (missingInput != null) {
            // 缺少必填槽位时只计划一个 ASK_USER 节点，主控会从同一白名单推导实际追问字段。
            return List.of(new CandidatePlanNode(
                    "ask-" + missingInput,
                    PlanNodeType.ASK_USER,
                    null,
                    List.of(),
                    List.of(),
                    FailurePolicy.FAIL));
        }
        List<InputReference> inputReferences = new ArrayList<>();
        // 必填输入全部用 SLOT 引用，Mock 不把用户原话或常量直接写进工具参数。
        definition.requiredInputs().forEach(input -> inputReferences.add(slotReference(input.name())));
        addOptionalInputReferences(definition, request.confirmedSlots(), inputReferences);
        // 推荐必须先于渲染节点完成，依赖关系由计划校验器和状态机共同检查。
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
            // 请求携带的名单只能缩小服务端权限，不能因为注册表存在工具就绕过调用方提供的白名单。
            return null;
        }
        // 同时检查注册定义仍为只读，防止未来误把同名写工具纳入这个最小请求流程。
        return toolRegistry.find(RankMoviePlanTool.TARGET_NAME)
                .filter(ToolDefinition::readOnly)
                .orElse(null);
    }

    private PlanValidationContext validationContext(PlanGenerationRequest request) {
        ToolDefinition definition = allowedRankMoviePlan(request);
        if (definition == null) {
            // 未授权工具时不给任何类型声明，确保 Mock 不能凭空通过 CALL_TOOL 的服务端校验。
            return new PlanValidationContext(
                    Map.of(), Map.of(), new SlotSnapshot(0L, request.confirmedSlots()));
        }
        Map<String, Class<?>> slotTypes = new LinkedHashMap<>();
        for (ToolInputDefinition input : definition.inputs()) {
            if (request.confirmedSlots().containsKey(input.name())) {
                // 只为实际存在的槽位声明类型；缺失槽位应触发 ASK_USER，而不是被伪装成 null 值参数。
                slotTypes.put(input.name(), input.valueType());
            }
        }
        return new PlanValidationContext(
                slotTypes, Map.of(), new SlotSnapshot(0L, request.confirmedSlots()));
    }

    private static String firstMissingRequiredInput(
            ToolDefinition definition, Map<String, String> confirmedSlots) {
        // requiredInputs 的顺序是追问顺序的一部分，不能改成无序 Set，否则测试与用户体验都会不稳定。
        return definition.requiredInputs().stream()
                .map(ToolInputDefinition::name)
                .filter(name -> !hasText(confirmedSlots.get(name)))
                .findFirst()
                .orElse(null);
    }

    private static InputReference slotReference(String name) {
        // source 与 referenceKey 使用同一槽位名，后续适配器据此从已校验快照取值并构造 D 的 Command。
        return new InputReference(name, InputReferenceSource.SLOT, name);
    }

    private static void addOptionalInputReferences(
            ToolDefinition definition, Map<String, String> confirmedSlots, List<InputReference> inputReferences) {
        boolean hasTimeFrom = hasText(confirmedSlots.get("timeFrom"));
        boolean hasTimeTo = hasText(confirmedSlots.get("timeTo"));
        for (ToolInputDefinition input : definition.inputs()) {
            if (input.required() || "timeFrom".equals(input.name()) || "timeTo".equals(input.name())) {
                continue;
            }
            if (hasText(confirmedSlots.get(input.name()))) {
                inputReferences.add(slotReference(input.name()));
            }
        }
        if (hasTimeFrom && hasTimeTo) {
            // 时间窗必须成对传递，避免半个区间改变 D 的查询语义。
            inputReferences.add(slotReference("timeFrom"));
            inputReferences.add(slotReference("timeTo"));
        }
    }

    private static String questionText(QuestionReplyFacts facts) {
        // 这是测试用固定文案；生产模型可润色措辞，但不能替换 facts 中指定的缺失字段。
        return "请补充" + facts.missingSlot() + "。";
    }

    private static String recommendationText(RecommendationReplyFacts facts, boolean purchaseEligible) {
        if (purchaseEligible) {
            // 可购数量只来自已经映射的候选列表，Mock 不生成价格、场次或库存等不存在的业务事实。
            return "已找到 " + facts.candidates().size() + " 个可购场次。";
        }
        // 无场次是 D 返回的成功降级，不要变成“工具失败”或虚构 showId、价格、开场时间。
        return "已找到影片候选，但当前条件下暂无可购场次。";
    }

    private static String recommendationPlanText(RecommendationPlanCardFacts facts) {
        return facts.plans().isEmpty()
                ? "当前条件下暂无可购方案。"
                : "已找到 " + facts.plans().size() + " 个可购方案。";
    }

    private static String travelAdviceText(TravelAdviceCardFacts facts) {
        return facts.available() ? "已查询到出行建议。" : "该出行任务暂未生成建议。";
    }

    private static String progressText(ProgressReplyFacts facts) {
        // PROCESSING 只提示节点仍在处理，不承诺稍后一定成功，也不暴露下游执行细节。
        return "推荐节点 " + facts.nodeId() + " 仍在处理中。";
    }

    private static String errorText(ErrorReplyFacts facts) {
        if (facts.errorCode() != null) {
            // 稳定错误码可供调用方结合 traceId 排查；异常原文不应进入用户回复或模型输入。
            return "当前请求暂时无法完成，错误码 " + facts.errorCode() + "。";
        }
        // 计划问题码不逐项回显，避免暴露服务端工具白名单和节点校验细节。
        return "当前请求未通过安全校验。";
    }

    private static String canonicalSlots(Map<String, String> slots) {
        // Map 的插入顺序不能影响计划 ID，因此先按槽位名排序后再拼接。
        return slots.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    private static String canonicalAllowedTools(PlanGenerationRequest request) {
        // 工具白名单同样需要规范化，否则同一集合的不同迭代顺序会导致测试结果不稳定。
        return request.allowedToolNames().stream().sorted().collect(Collectors.joining(","));
    }

    private static String fingerprint(String... values) {
        try {
            // 计划 ID 只需在 Mock 场景内稳定，不承载安全令牌或幂等语义，因此截取 SHA-256 前 8 字节即可。
            String source = SCENARIO_VERSION + "|" + String.join("|", values);
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                result.append(String.format("%02x", bytes[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 是 JRE 必备算法；缺失表示运行环境异常，继续生成弱指纹会破坏测试可重复性。
            throw new IllegalStateException("Java 运行环境缺少 SHA-256", exception);
        }
    }

    private static boolean hasText(String value) {
        // 与主控保持一致：空白字符串不能被视为已确认槽位。
        return value != null && !value.isBlank();
    }
}
