package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationValidationFailure;

/** 确认用例的安全结果；调用层不得从中反推 Command 或 A 的完整订单。 */
public record AgentConfirmationResult(
        AgentConfirmationAction action,
        AgentConfirmationValidationFailure validationFailure,
        boolean writeToolInvoked) {
    public AgentConfirmationResult {
        if (action == null) {
            throw new IllegalArgumentException("action 不能为空");
        }
        if (validationFailure != null && writeToolInvoked) {
            throw new IllegalArgumentException("校验失败时不得调用写工具");
        }
    }
}
