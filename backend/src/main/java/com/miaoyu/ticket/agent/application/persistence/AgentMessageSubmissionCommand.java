package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;

/** 最小只读消息提交输入；用户身份只从认证上下文获得。 */
public record AgentMessageSubmissionCommand(
        String sessionId,
        String content,
        String clientRequestId,
        SlotSnapshot slotSnapshot,
        PlanValidationContext validationContext,
        long remainingDeadlineMs) {
}
