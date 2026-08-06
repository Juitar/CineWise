package com.miaoyu.ticket.agent.application.run;

import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import java.util.Objects;

/** 多工具 Supervisor 的一次受控运行输入；不携带用户身份、确认凭证或写工具参数。 */
public record MultiToolSupervisorRequest(
        String clientRequestId,
        String input,
        PlanValidationContext validationContext,
        String runId,
        String traceId,
        long remainingDeadlineMs) {

    public MultiToolSupervisorRequest {
        requireText(clientRequestId, "clientRequestId");
        requireText(input, "input");
        validationContext = Objects.requireNonNull(validationContext, "计划校验上下文不能为空");
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
