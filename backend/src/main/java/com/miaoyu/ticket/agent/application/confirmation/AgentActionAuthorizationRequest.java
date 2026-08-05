package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import java.util.List;
import java.util.Objects;

/** A 传给 B 的最小授权校验输入，不携带用户、金额、状态或参数摘要。 */
public record AgentActionAuthorizationRequest(
        String actionId,
        ToolContext context,
        String showId,
        List<String> seatIds) {

    public AgentActionAuthorizationRequest {
        if (actionId == null || actionId.isBlank() || actionId.length() > 36) {
            throw new IllegalArgumentException("actionId 必须为 1 至 36 个字符");
        }
        context = Objects.requireNonNull(context, "context 不能为空");
        context.requireWriteRequestIdentifiers();
        seatIds = List.copyOf(Objects.requireNonNull(seatIds, "seatIds 不能为空"));
    }

    /** 由 ToolContext 的白名单工具名和 A 提交的选择重建受控 Command。 */
    public ConfirmedOrderCommand toConfirmedOrderCommand() {
        return new ConfirmedOrderCommand(context.targetName(), showId, seatIds);
    }
}
