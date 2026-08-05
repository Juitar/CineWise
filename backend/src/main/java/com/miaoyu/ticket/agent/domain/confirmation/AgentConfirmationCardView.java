package com.miaoyu.ticket.agent.domain.confirmation;

import java.time.LocalDateTime;
import java.util.Objects;

/** 允许写入 SSE 确认卡的最小安全投影，不含 Command、摘要、写键或订单数据。 */
public record AgentConfirmationCardView(
        String actionId,
        int planVersion,
        LocalDateTime expireAt,
        AgentConfirmationActionStatus status,
        String displayText) {

    public AgentConfirmationCardView {
        requireText(actionId, "actionId");
        if (planVersion < 1) {
            throw new IllegalArgumentException("planVersion 必须大于零");
        }
        Objects.requireNonNull(expireAt, "expireAt 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        requireText(displayText, "displayText");
    }

    public static AgentConfirmationCardView from(AgentConfirmationAction action, String displayText) {
        Objects.requireNonNull(action, "action 不能为空");
        return new AgentConfirmationCardView(
                action.actionId(), action.planVersion(), action.expireAt(), action.status(), displayText);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }
}
