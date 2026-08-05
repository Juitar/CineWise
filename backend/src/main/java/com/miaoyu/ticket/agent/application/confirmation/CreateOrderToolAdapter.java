package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;

/** A 的正式建单 Tool 确认前，仅允许提供测试 Mock；不得实现本机 HTTP 调用。 */
public interface CreateOrderToolAdapter {
    ToolResult<CreateOrderToolResult> execute(ToolContext context, ConfirmedOrderCommand command);

    ToolResult<CreateOrderToolResult> queryByOriginalIdentifiers(ToolContext context, ConfirmedOrderCommand command);
}
