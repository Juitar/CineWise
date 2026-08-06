package com.miaoyu.ticket.agent.domain.confirmation;

import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import java.time.LocalDateTime;
import java.util.Objects;

/** 确认时重新读取的受控运行和业务校验事实。 */
public record AgentConfirmationValidationContext(
        long currentUserId,
        AgentRunStatus runStatus,
        String planId,
        int planVersion,
        PlanNodeStatus nodeStatus,
        AgentActionParameterHash currentParameterHash,
        boolean businessDataValid,
        LocalDateTime now) {

    public AgentConfirmationValidationContext {
        if (currentUserId <= 0L) {
            throw new IllegalArgumentException("当前用户 ID 必须为正数");
        }
        Objects.requireNonNull(runStatus, "runStatus 不能为空");
        requireText(planId, "planId");
        if (planVersion < 1) {
            throw new IllegalArgumentException("planVersion 必须大于零");
        }
        Objects.requireNonNull(nodeStatus, "nodeStatus 不能为空");
        Objects.requireNonNull(currentParameterHash, "currentParameterHash 不能为空");
        Objects.requireNonNull(now, "now 不能为空");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }
}
