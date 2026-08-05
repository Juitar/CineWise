package com.miaoyu.ticket.agent.domain.confirmation;

import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import java.util.Objects;
import java.util.Optional;

/** 不依赖持久化或 A 实现的确认资格校验顺序。 */
public final class AgentConfirmationActionValidator {
    /** 返回首个可安全展示的固定拒绝原因；通过时返回空。 */
    public Optional<AgentConfirmationValidationFailure> validate(
            AgentConfirmationAction action,
            AgentConfirmationValidationContext context) {
        Objects.requireNonNull(action, "action 不能为空");
        Objects.requireNonNull(context, "context 不能为空");
        if (action.userId() != context.currentUserId()) {
            return Optional.of(AgentConfirmationValidationFailure.NOT_OWNER);
        }
        if (action.status() != AgentConfirmationActionStatus.PENDING_CONFIRMATION) {
            return Optional.of(AgentConfirmationValidationFailure.ACTION_NOT_CONFIRMABLE);
        }
        if (action.isExpiredAt(context.now())) {
            return Optional.of(AgentConfirmationValidationFailure.EXPIRED);
        }
        if (context.runStatus() != AgentRunStatus.RUNNING) {
            return Optional.of(AgentConfirmationValidationFailure.RUN_ENDED);
        }
        if (!action.planId().equals(context.planId()) || action.planVersion() != context.planVersion()) {
            return Optional.of(AgentConfirmationValidationFailure.PLAN_CHANGED);
        }
        if (context.nodeStatus() != PlanNodeStatus.WAITING_CONFIRMATION) {
            return Optional.of(AgentConfirmationValidationFailure.NODE_NOT_WAITING_CONFIRMATION);
        }
        if (!action.parameterHash().equals(context.currentParameterHash())) {
            return Optional.of(AgentConfirmationValidationFailure.PARAMETERS_CHANGED);
        }
        if (!context.businessDataValid()) {
            return Optional.of(AgentConfirmationValidationFailure.BUSINESS_DATA_INVALID);
        }
        return Optional.empty();
    }
}
