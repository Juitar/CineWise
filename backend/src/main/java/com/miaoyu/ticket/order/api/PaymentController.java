package com.miaoyu.ticket.order.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.order.application.PaymentApplicationService;
import com.miaoyu.ticket.order.application.PaymentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mock支付和支付结果恢复REST适配层，请求契约不包含模拟密码。 */
@Validated
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Mock支付")
@SecurityRequirement(name = "cookieAuth")
public class PaymentController {

    private final PaymentApplicationService paymentApplicationService;

    public PaymentController(PaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
    }

    /** 支付页在浏览器本地清空六位输入后，以空业务请求体调用本接口。 */
    @PostMapping("/{orderNo}/payments")
    @Operation(summary = "幂等完成本人订单Mock支付")
    public Result<PaymentResponse> pay(
            @PathVariable
            @NotBlank
            @Size(max = 32)
            String orderNo,
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Size(max = 64)
            String idempotencyKey) {
        return Result.success(toResponse(paymentApplicationService.pay(orderNo, idempotencyKey)));
    }

    /** 写响应未知时只查询原支付，不自动重放POST。 */
    @GetMapping("/{orderNo}/payment")
    @Operation(summary = "查询本人订单Mock支付结果")
    public Result<PaymentResponse> queryPayment(
            @PathVariable
            @NotBlank
            @Size(max = 32)
            String orderNo) {
        return Result.success(toResponse(paymentApplicationService.queryPayment(orderNo)));
    }

    private PaymentResponse toResponse(PaymentView payment) {
        String ticketId = payment.ticketId() == null ? null : payment.ticketId().toString();
        return new PaymentResponse(
                Long.toString(payment.orderId()),
                payment.orderNo(),
                payment.paymentNo(),
                payment.orderStatus().name(),
                payment.paymentStatus().name(),
                ticketId,
                payment.stateVersion(),
                toOffsetDateTime(payment.updatedAt()));
    }

    private OffsetDateTime toOffsetDateTime(java.time.LocalDateTime value) {
        return value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }
}
