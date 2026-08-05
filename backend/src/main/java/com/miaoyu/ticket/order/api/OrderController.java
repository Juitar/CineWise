package com.miaoyu.ticket.order.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.api.PageResult;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.order.application.CreateOrderCommand;
import com.miaoyu.ticket.order.application.OrderApplicationService;
import com.miaoyu.ticket.order.application.OrderCancellationService;
import com.miaoyu.ticket.order.application.OrderListQuery;
import com.miaoyu.ticket.order.application.OrderPageView;
import com.miaoyu.ticket.order.application.OrderQueryService;
import com.miaoyu.ticket.order.application.OrderQueryView;
import com.miaoyu.ticket.order.application.OrderView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 原子建单与丢失响应恢复的REST适配层。 */
@Validated
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "订单交易")
@SecurityRequirement(name = "cookieAuth")
public class OrderController {

    private final OrderApplicationService orderApplicationService;
    private final OrderQueryService orderQueryService;
    private final OrderCancellationService orderCancellationService;

    public OrderController(
            OrderApplicationService orderApplicationService,
            OrderQueryService orderQueryService,
            OrderCancellationService orderCancellationService) {
        this.orderApplicationService = orderApplicationService;
        this.orderQueryService = orderQueryService;
        this.orderCancellationService = orderCancellationService;
    }

    /** 服务端从认证上下文取用户并从场次重新计算金额。 */
    @PostMapping
    @Operation(summary = "原子锁座并创建待支付订单")
    public Result<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Size(max = 64)
            String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        List<Long> seatIds = request.seatIds().stream()
                .map(this::parseBusinessId)
                .toList();
        OrderView order = orderApplicationService.createOrder(new CreateOrderCommand(
                parseBusinessId(request.showId()),
                seatIds,
                request.clientRequestId(),
                idempotencyKey));
        return Result.success(toResponse(order));
    }

    /** 建单响应未知时使用原请求标识恢复，不发起第二次建单。 */
    @GetMapping("/by-request/{clientRequestId}")
    @Operation(summary = "按客户端请求标识恢复本人订单")
    public Result<OrderResponse> queryByClientRequestId(
            @Parameter(description = "原建单请求标识")
            @PathVariable
            @NotBlank
            @Size(max = 64)
            String clientRequestId) {
        return Result.success(toResponse(
                orderApplicationService.queryByClientRequestId(clientRequestId)));
    }

    /** 列表只返回当前用户订单，状态和分页边界由应用服务统一校验。 */
    @GetMapping
    @Operation(summary = "分页查询本人订单")
    public Result<PageResult<OrderQueryResponse>> queryOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        OrderPageView result = orderQueryService.queryOrders(new OrderListQuery(
                orderNo,
                status,
                dateFrom,
                dateTo,
                page,
                size));
        return Result.success(new PageResult<>(
                result.total(),
                result.page(),
                result.size(),
                result.records().stream().map(this::toQueryResponse).toList()));
    }

    /** 跨用户订单号与不存在统一映射为404，不暴露资源存在性。 */
    @GetMapping("/{orderNo}")
    @Operation(summary = "查询本人订单详情")
    public Result<OrderQueryResponse> queryOrder(
            @PathVariable
            @NotBlank
            @Size(max = 32)
            String orderNo) {
        return Result.success(toQueryResponse(orderQueryService.queryOrder(orderNo)));
    }

    /** 取消只接收订单号和动作幂等键，身份始终来自认证上下文。 */
    @PostMapping("/{orderNo}/cancel")
    @Operation(summary = "幂等取消本人待支付订单")
    public Result<OrderResponse> cancelOrder(
            @PathVariable
            @NotBlank
            @Size(max = 32)
            String orderNo,
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Size(max = 64)
            String idempotencyKey) {
        return Result.success(toResponse(
                orderCancellationService.cancelOrder(orderNo, idempotencyKey)));
    }

    private long parseBusinessId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException("ID must be positive");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "业务ID必须是正整数");
        }
    }

    private OrderResponse toResponse(OrderView order) {
        return new OrderResponse(
                Long.toString(order.orderId()),
                order.orderNo(),
                Long.toString(order.showId()),
                order.seatIds().stream().map(String::valueOf).toList(),
                order.ticketCount(),
                order.unitPrice().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                order.totalAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                order.status().name(),
                toOffsetDateTime(order.expireTime()),
                order.stateVersion(),
                toOffsetDateTime(order.updatedAt()));
    }

    /** 个人订单GET只暴露A的场次关联ID和时间，不复制D的内容字段。 */
    private OrderQueryResponse toQueryResponse(OrderQueryView order) {
        return new OrderQueryResponse(
                Long.toString(order.orderId()),
                order.orderNo(),
                Long.toString(order.showId()),
                Long.toString(order.movieId()),
                Long.toString(order.cinemaId()),
                toOffsetDateTime(order.showStartTime()),
                order.seatIds().stream().map(String::valueOf).toList(),
                order.ticketCount(),
                order.unitPrice().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                order.totalAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                order.status().name(),
                toOffsetDateTime(order.expireTime()),
                order.stateVersion(),
                toOffsetDateTime(order.updatedAt()));
    }

    private OffsetDateTime toOffsetDateTime(java.time.LocalDateTime value) {
        return value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }
}
