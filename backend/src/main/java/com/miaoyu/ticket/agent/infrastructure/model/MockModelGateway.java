package com.miaoyu.ticket.agent.infrastructure.model;

import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.model.PlanGenerationRequest;
import com.miaoyu.ticket.agent.application.model.PlanGenerationResponse;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationRequest;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlanNode;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 不访问网络和当前时间的固定场景模型实现，仅用于开发和测试。 */
public final class MockModelGateway implements ModelGateway {
    private static final String SCENARIO_VERSION = "mock-plan-v1";

    private final PlanSchemaValidator planSchemaValidator;
    private final PlanValidationContext validationContext;

    public MockModelGateway(PlanSchemaValidator planSchemaValidator, PlanValidationContext validationContext) {
        this.planSchemaValidator = Objects.requireNonNull(planSchemaValidator, "planSchemaValidator 不能为空");
        this.validationContext = Objects.requireNonNull(validationContext, "validationContext 不能为空");
    }

    @Override
    public PlanGenerationResponse generatePlan(PlanGenerationRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        String allowedTools = request.allowedToolNames().stream().sorted().collect(Collectors.joining(","));
        String fingerprint = fingerprint(request.clientRequestId(), request.input(), allowedTools);
        CandidatePlan candidatePlan = new CandidatePlan(
                "mock-" + fingerprint,
                1,
                List.of(new CandidatePlanNode(
                        "render-result",
                        PlanNodeType.RENDER_RESULT,
                        null,
                        List.of(),
                        List.of(),
                        FailurePolicy.FAIL)));
        return new PlanGenerationResponse(
                candidatePlan,
                planSchemaValidator.validate(candidatePlan, validationContext));
    }

    @Override
    public ReplyGenerationResponse generateReply(ReplyGenerationRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        return new ReplyGenerationResponse("mock-reply-" + fingerprint(request.clientRequestId(), request.input(), ""));
    }

    private static String fingerprint(String clientRequestId, String input, String allowedTools) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((SCENARIO_VERSION + "|" + clientRequestId + "|" + input + "|" + allowedTools)
                            .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                result.append(String.format("%02x", bytes[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java 运行环境缺少 SHA-256", exception);
        }
    }
}
