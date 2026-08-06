package com.miaoyu.ticket.agent.infrastructure.tool;

import com.miaoyu.ticket.agent.application.confirmation.CreateOrderToolResult;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.order.api.AgentOrderResult;
import com.miaoyu.ticket.order.api.CreateOrderForAgentCommand;
import com.miaoyu.ticket.order.api.CreateOrderTool;
import org.springframework.stereotype.Component;

/** B 到 A 的唯一生产建单入口；不访问订单 Application Service 或持久化层。 */
@Component
public class CreateOrderToolAdapter
        implements com.miaoyu.ticket.agent.application.confirmation.CreateOrderToolAdapter {
    private final CreateOrderTool createOrderTool;

    public CreateOrderToolAdapter(CreateOrderTool createOrderTool) {
        this.createOrderTool = createOrderTool;
    }

    @Override
    public ToolResult<CreateOrderToolResult> execute(
            String actionId, ToolContext context, ConfirmedOrderCommand command) {
        ToolResult<AgentOrderResult> result = createOrderTool.execute(
                context, new CreateOrderForAgentCommand(actionId, command.showId(), command.sortedSeatIds()));
        return map(result);
    }

    @Override
    public ToolResult<CreateOrderToolResult> queryByOriginalIdentifiers(
            ToolContext context, ConfirmedOrderCommand command) {
        return map(createOrderTool.queryByClientRequestId(context));
    }

    private ToolResult<CreateOrderToolResult> map(ToolResult<AgentOrderResult> result) {
        CreateOrderToolResult data = result.data() == null ? null : new CreateOrderToolResult(result.data().orderId());
        return new ToolResult<>(result.status(), data, result.errorCode(), result.retryable(), result.replanSuggested(),
                result.suggestedNextAction(), result.degraded(), result.fallbackType(), result.stateVersion(),
                result.dataAt(), result.expiresAt());
    }
}
