package com.miaoyu.ticket.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.order.application.OrderApplicationService;
import com.miaoyu.ticket.order.application.OrderErrorCode;
import com.miaoyu.ticket.order.application.OrderView;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.ticketing.application.TicketingErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 验证 Agent 建单适配器的边界、幂等上下文和结果未知恢复语义。 */
@ExtendWith(MockitoExtension.class)
class CreateOrderToolTest {

    private static final String ACTION_ID = "123e4567-e89b-42d3-a456-426614174000";
    private static final LocalDateTime EXPIRE_TIME = LocalDateTime.of(2026, 8, 5, 20, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 8, 5, 18, 0);

    @Mock
    private OrderApplicationService orderApplicationService;

    @Test
    void givenConfirmedCommand_whenCreateSucceeds_thenReturnTypedOrderAndForwardStableKeys() {
        CreateOrderTool tool = new CreateOrderTool(orderApplicationService);
        CreateOrderForAgentCommand command = command();
        OrderView order = order();
        when(orderApplicationService.createOrder(org.mockito.ArgumentMatchers.any())).thenReturn(order);

        ToolResult<AgentOrderResult> result = tool.execute(context(), command);

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        assertThat(result.data().orderId()).isEqualTo("90001");
        assertThat(result.data().showId()).isEqualTo("70001");
        assertThat(result.data().seatIds()).containsExactly("80001", "80002");
        assertThat(result.data().totalAmount()).isEqualTo("78.00");
        assertThat(result.stateVersion()).isEqualTo(3L);
        assertThat(result.data().stateVersion()).isEqualTo(7);
        verify(orderApplicationService).createOrder(org.mockito.ArgumentMatchers.argThat(request ->
                request.showId() == 70001L
                        && request.seatIds().equals(List.of(80001L, 80002L))
                        && request.clientRequestId().equals("client-request-1")
                        && request.idempotencyKey().equals("idem-1")));
    }

    @Test
    void givenBusinessFailure_whenCreateToolRuns_thenReturnStableFailedResultWithoutRetry() {
        CreateOrderTool tool = new CreateOrderTool(orderApplicationService);
        when(orderApplicationService.createOrder(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BusinessException(TicketingErrorCode.SEAT_NOT_LOCKABLE));

        ToolResult<AgentOrderResult> result = tool.execute(context(), command());

        assertThat(result.status()).isEqualTo(ToolStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(204001);
        assertThat(result.retryable()).isFalse();
        assertThat(result.stateVersion()).isEqualTo(3L);
    }

    @Test
    void givenUnconfirmedRuntimeOutcome_whenCreateToolRuns_thenReturnProcessingWithoutRetry() {
        CreateOrderTool tool = new CreateOrderTool(orderApplicationService);
        when(orderApplicationService.createOrder(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("database response unavailable"));

        ToolResult<AgentOrderResult> result = tool.execute(context(), command());

        assertThat(result.status()).isEqualTo(ToolStatus.PROCESSING);
        assertThat(result.retryable()).isFalse();
        assertThat(result.suggestedNextAction()).isEqualTo(CreateOrderTool.QUERY_ORIGINAL_ORDER);
        assertThat(result.stateVersion()).isEqualTo(3L);
    }

    @Test
    void givenOriginalRequest_whenQueryRecoveryRuns_thenReturnSameOrderWithoutCreateCall() {
        CreateOrderTool tool = new CreateOrderTool(orderApplicationService);
        when(orderApplicationService.queryByClientRequestId("client-request-1")).thenReturn(order());

        ToolResult<AgentOrderResult> result = tool.queryByClientRequestId(context());

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        assertThat(result.data().orderNo()).isEqualTo("CW90001");
        verify(orderApplicationService).queryByClientRequestId("client-request-1");
        verify(orderApplicationService, never()).createOrder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void givenInvalidCommandOrToolTarget_whenExecuteRuns_thenRejectBeforeOrderService() {
        CreateOrderTool tool = new CreateOrderTool(orderApplicationService);
        assertThatThrownBy(() -> new CreateOrderForAgentCommand(ACTION_ID, "+70001", List.of("80001")))
                .isInstanceOf(IllegalArgumentException.class);

        ToolResult<AgentOrderResult> result = tool.execute(
                new ToolContext("run-1", "node-1", "wrongTool", List.of("slots.showId"), 1_000L,
                        "trace-1", "client-request-1", "idem-1", 3L),
                command());

        assertThat(result.status()).isEqualTo(ToolStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(100001);
        verifyNoInteractions(orderApplicationService);
    }

    @Test
    void givenMissingClientRequestId_whenQueryRecoveryRuns_thenReturnParameterFailure() {
        CreateOrderTool tool = new CreateOrderTool(orderApplicationService);

        ToolResult<AgentOrderResult> result = tool.queryByClientRequestId(
                new ToolContext("run-1", "node-1", CreateOrderTool.TARGET_NAME, List.of(), 1_000L,
                        "trace-1", " ", null, 3L));

        assertThat(result.status()).isEqualTo(ToolStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(100001);
        verifyNoInteractions(orderApplicationService);
    }

    @Test
    void givenCurrentAvailableSelection_whenValidateRuns_thenReturnExecutableWithoutCreate() {
        CreateOrderTool tool = new CreateOrderTool(orderApplicationService);

        OrderPrecheckResult result = tool.validate(
                new CreateOrderPrecheckCommand("70001", List.of("80001", "80002")));

        assertThat(result.executable()).isTrue();
        assertThat(result.errorCode()).isNull();
        verify(orderApplicationService).validateOrderSelection(70001L, List.of(80001L, 80002L));
        verify(orderApplicationService, never()).createOrder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void givenStaleSelection_whenValidateRuns_thenReturnSafeErrorWithoutOrderData() {
        CreateOrderTool tool = new CreateOrderTool(orderApplicationService);
        doThrow(new BusinessException(TicketingErrorCode.SEAT_NOT_LOCKABLE))
                .when(orderApplicationService)
                .validateOrderSelection(70001L, List.of(80001L));

        OrderPrecheckResult result = tool.validate(
                new CreateOrderPrecheckCommand("70001", List.of("80001")));

        assertThat(result.executable()).isFalse();
        assertThat(result.errorCode()).isEqualTo(204001);
        verify(orderApplicationService, never()).createOrder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void givenInvalidSelection_whenValidateRuns_thenReturnInvalidParameterWithoutServiceCall() {
        CreateOrderTool tool = new CreateOrderTool(orderApplicationService);

        OrderPrecheckResult result = tool.validate(
                new CreateOrderPrecheckCommand("+70001", List.of("80001")));

        assertThat(result.executable()).isFalse();
        assertThat(result.errorCode()).isEqualTo(100001);
        verifyNoInteractions(orderApplicationService);
    }

    @Test
    void givenQueryDependencyUnavailable_whenValidateRuns_thenReturnStableUnavailableCode() {
        CreateOrderTool tool = new CreateOrderTool(orderApplicationService);
        doThrow(new IllegalStateException("database unavailable"))
                .when(orderApplicationService)
                .validateOrderSelection(70001L, List.of(80001L));

        OrderPrecheckResult result = tool.validate(
                new CreateOrderPrecheckCommand("70001", List.of("80001")));

        assertThat(result.executable()).isFalse();
        assertThat(result.errorCode()).isEqualTo(306003);
        verify(orderApplicationService, never()).createOrder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void givenNoOriginalOrder_whenQueryRecoveryRuns_thenReturnOrderNotFoundFailure() {
        CreateOrderTool tool = new CreateOrderTool(orderApplicationService);
        when(orderApplicationService.queryByClientRequestId("client-request-1"))
                .thenThrow(new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        ToolResult<AgentOrderResult> result = tool.queryByClientRequestId(context());

        assertThat(result.status()).isEqualTo(ToolStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(205001);
        assertThat(result.retryable()).isFalse();
    }

    private CreateOrderForAgentCommand command() {
        return new CreateOrderForAgentCommand(ACTION_ID, "70001", List.of("80001", "80002"));
    }

    private ToolContext context() {
        return new ToolContext("run-1", "node-1", CreateOrderTool.TARGET_NAME, List.of("slots.showId"),
                1_000L, "trace-1", "client-request-1", "idem-1", 3L);
    }

    private OrderView order() {
        return new OrderView(
                90001L,
                "CW90001",
                70001L,
                List.of(80001L, 80002L),
                2,
                new BigDecimal("39.00"),
                new BigDecimal("78.00"),
                OrderStatus.PENDING_PAYMENT,
                EXPIRE_TIME,
                7,
                UPDATED_AT);
    }
}
