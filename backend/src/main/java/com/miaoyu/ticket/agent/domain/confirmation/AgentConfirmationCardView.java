package com.miaoyu.ticket.agent.domain.confirmation;

import java.time.LocalDateTime;
import java.util.Objects;

/** 允许写入 SSE 确认卡的最小安全投影，不含 Command、摘要、写键或订单数据。 */
public record AgentConfirmationCardView(
        String actionId,
        AgentConfirmationActionType actionType,
        LocalDateTime expireAt,
        AgentConfirmationCardStatus status,
        String displayTitle,
        java.util.List<String> displayLines) {

    public AgentConfirmationCardView {
        requireSafeText(actionId, "actionId");
        Objects.requireNonNull(actionType, "actionType 不能为空");
        Objects.requireNonNull(expireAt, "expireAt 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        displayTitle = normalizeOptionalText(displayTitle, "displayTitle");
        displayLines = java.util.List.copyOf(Objects.requireNonNull(displayLines, "displayLines 不能为空"));
        displayLines.forEach(line -> requireSafeText(line, "displayLines 元素"));
    }

    public static AgentConfirmationCardView from(
            AgentConfirmationAction action,
            String displayTitle,
            java.util.List<String> displayLines) {
        Objects.requireNonNull(action, "action 不能为空");
        return new AgentConfirmationCardView(
                action.actionId(),
                AgentConfirmationActionType.CREATE_ORDER,
                action.expireAt(),
                AgentConfirmationCardStatus.fromActionStatus(action.status()),
                displayTitle,
                displayLines);
    }

    private static String normalizeOptionalText(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        requireSafeText(value, fieldName);
        return value;
    }

    private static void requireSafeText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
            throw new IllegalArgumentException(fieldName + "不能包含 HTML 标记");
        }
    }
}
