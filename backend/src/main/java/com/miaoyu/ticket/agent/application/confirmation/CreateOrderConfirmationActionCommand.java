package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import java.util.Objects;

/** 仅由已校验的 Agent 计划执行器构造的建单确认输入，不属于前端 DTO。 */
public record CreateOrderConfirmationActionCommand(
        String runId,
        String planId,
        int planVersion,
        String nodeId,
        ConfirmedOrderCommand command) {

    public CreateOrderConfirmationActionCommand {
        requireText(runId, "runId");
        requireText(planId, "planId");
        if (planVersion < 1) {
            throw new IllegalArgumentException("planVersion 必须大于零");
        }
        requireText(nodeId, "nodeId");
        Objects.requireNonNull(command, "command 不能为空");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }
}
