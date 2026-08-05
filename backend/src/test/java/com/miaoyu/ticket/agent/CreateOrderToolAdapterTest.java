package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.agent.infrastructure.tool.CreateOrderToolAdapter;
import com.miaoyu.ticket.order.api.AgentOrderResult;
import com.miaoyu.ticket.order.api.CreateOrderForAgentCommand;
import com.miaoyu.ticket.order.api.CreateOrderTool;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** B 的生产适配器只调用 A 的公开 Tool，不把 traceId 当作 actionId。 */
class CreateOrderToolAdapterTest {
    private static final String ACTION_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    @Test
    void shouldPassActionIdAndServerControlledCommandToA() {
        CreateOrderTool tool = mock(CreateOrderTool.class);
        CreateOrderToolAdapter adapter = new CreateOrderToolAdapter(tool);
        when(tool.execute(any(), any())).thenReturn(processing());

        ToolResult<?> result = adapter.execute(ACTION_ID, context(), command());

        ArgumentCaptor<CreateOrderForAgentCommand> request = ArgumentCaptor.forClass(CreateOrderForAgentCommand.class);
        verify(tool).execute(any(), request.capture());
        assertEquals(ACTION_ID, request.getValue().actionId());
        assertEquals("70001", request.getValue().showId());
        assertEquals(List.of("2", "4"), request.getValue().seatIds());
        assertEquals(ToolStatus.PROCESSING, result.status());
    }

    @Test
    void shouldRecoverOnlyThroughAOriginalRequestQuery() {
        CreateOrderTool tool = mock(CreateOrderTool.class);
        CreateOrderToolAdapter adapter = new CreateOrderToolAdapter(tool);
        when(tool.queryByClientRequestId(any())).thenReturn(processing());

        ToolResult<?> result = adapter.queryByOriginalIdentifiers(context(), command());

        assertEquals(ToolStatus.PROCESSING, result.status());
        verify(tool).queryByClientRequestId(any());
        verify(tool, never()).execute(any(), any());
    }

    private static ConfirmedOrderCommand command() {
        return new ConfirmedOrderCommand("createOrder", "70001", List.of("4", "2"));
    }

    private static ToolContext context() {
        return new ToolContext("run-1", "confirm-order", "createOrder", List.of(), 3_000L, "trace-1",
                "agent-act-client", "agent-order-idempotency", 1L);
    }

    private static ToolResult<AgentOrderResult> processing() {
        return new ToolResult<>(ToolStatus.PROCESSING, null, null, false, false, "QUERY_ORIGINAL_ORDER", false,
                null, 1L, null, null);
    }
}
