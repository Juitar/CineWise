package com.miaoyu.ticket.agent.application.run;

import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import java.util.Objects;

/** 最小只读主控的可信应用请求，不包含用户身份、会话或模型候选计划。 */
public record MinimalReadOnlyAgentRequest(
        String clientRequestId,
        String input,
        PlanValidationContext validationContext,
        String runId,
        String traceId,
        long remainingDeadlineMs) {

    public MinimalReadOnlyAgentRequest {
        requireText(clientRequestId, "clientRequestId");
        requireText(input, "input");
        validationContext = Objects.requireNonNull(validationContext, "validationContext 不能为空");
        requireText(runId, "runId");
        requireText(traceId, "traceId");
        if (remainingDeadlineMs <= 0L) {
            throw new IllegalArgumentException("remainingDeadlineMs 必须大于 0");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }
}
