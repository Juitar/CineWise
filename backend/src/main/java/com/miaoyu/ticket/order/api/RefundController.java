package com.miaoyu.ticket.order.api;

import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.order.application.AlternativeShowView;
import com.miaoyu.ticket.order.application.AlternativeShowsView;
import com.miaoyu.ticket.order.application.RefundApplicationService;
import com.miaoyu.ticket.order.application.RefundCommand;
import com.miaoyu.ticket.order.application.RefundImpactView;
import com.miaoyu.ticket.order.application.RefundView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人退票影响、原子退票、结果恢复和替代场次REST适配层。
 *
 * <ul>
 *   <li>影响查询使用POST表达用户主动发起确认流程，但服务端保持只读；</li>
 *   <li>退款写请求必须携带Idempotency-Key；</li>
 *   <li>结果未知通过独立GET查询，不重发POST；</li>
 *   <li>替代场次与退款写事务完全解耦；</li>
 *   <li>所有接口通过cookieAuth声明复用C的认证能力。</li>
 * </ul>
 * <p>Controller只做传输映射；身份、资格、金额、幂等和状态迁移均由Application Service决定。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "模拟退票")
@SecurityRequirement(name = "cookieAuth")
public class RefundController {

    private final RefundApplicationService refundApplicationService;

    public RefundController(RefundApplicationService refundApplicationService) {
        this.refundApplicationService = refundApplicationService;
    }

    /** 查询仅产生影响摘要，页面展示后由用户主动触发独立写请求。 */
    @PostMapping("/{orderNo}/refund-confirmation")
    @Operation(summary = "查询本人订单退票影响")
    public Result<RefundImpactResponse> queryRefundImpact(
            @PathVariable
            @NotBlank
            @Size(max = 32)
            String orderNo) {
        return Result.success(toImpactResponse(refundApplicationService.queryImpact(orderNo)));
    }

    /** 传统页面确认后使用稳定幂等键提交；网络层不得自动重试。 */
    @PostMapping("/{orderNo}/refunds")
    @Operation(summary = "幂等完成本人订单模拟退票")
    public Result<RefundResponse> requestRefund(
            @PathVariable
            @NotBlank
            @Size(max = 32)
            String orderNo,
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Size(max = 64)
            String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request) {
        RefundCommand command = new RefundCommand(
                orderNo,
                request.refundReason(),
                request.clientRequestId(),
                request.actionId(),
                idempotencyKey);
        return Result.success(toRefundResponse(refundApplicationService.requestRefund(command)));
    }

    /** 写响应未知时查询原退款，不重新执行退票。 */
    @GetMapping("/{orderNo}/refund")
    @Operation(summary = "查询本人订单模拟退票结果")
    public Result<RefundResponse> queryRefund(
            @PathVariable
            @NotBlank
            @Size(max = 32)
            String orderNo) {
        return Result.success(toRefundResponse(refundApplicationService.queryRefund(orderNo)));
    }

    /** 替代场次查询与退票事务解耦，无候选时返回空数组。 */
    @GetMapping("/{orderNo}/alternative-shows")
    @Operation(summary = "查询本人订单同影片替代场次")
    public Result<AlternativeShowsResponse> queryAlternativeShows(
            @PathVariable
            @NotBlank
            @Size(max = 32)
            String orderNo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo) {
        return Result.success(toAlternativeResponse(
                refundApplicationService.queryAlternativeShows(orderNo, dateFrom, dateTo)));
    }

    private RefundImpactResponse toImpactResponse(RefundImpactView impact) {
        // API边界统一完成ID、金额和时间格式转换，Application层保持精确领域类型。
        // 映射不会添加可退结论之外的新业务规则。
        return new RefundImpactResponse(
                Long.toString(impact.orderId()),
                impact.orderNo(),
                money(impact.refundAmount()),
                impact.orderStatus().name(),
                impact.ticketStatus().name(),
                toOffsetDateTime(impact.showStartTime()),
                impact.orderVersion(),
                impact.ticketVersion(),
                impact.impactText());
    }

    private RefundResponse toRefundResponse(RefundView refund) {
        // 写入和恢复复用相同映射，避免断网恢复页面得到不同字段或状态语义。
        // 内部幂等键和JSON影响快照不会穿透到客户端。
        return new RefundResponse(
                Long.toString(refund.refundId()),
                refund.refundNo(),
                Long.toString(refund.orderId()),
                refund.orderNo(),
                refund.refundStatus().name(),
                money(refund.refundAmount()),
                refund.orderStatus().name(),
                refund.ticketStatus().name(),
                refund.stateVersion(),
                toOffsetDateTime(refund.updatedAt()));
    }

    private AlternativeShowsResponse toAlternativeResponse(AlternativeShowsView alternatives) {
        return new AlternativeShowsResponse(
                alternatives.orderNo(),
                alternatives.shows().stream().map(this::toAlternativeShowResponse).toList());
    }

    private AlternativeShowResponse toAlternativeShowResponse(AlternativeShowView show) {
        // 内容名称不在A响应中临时拼接；C需要展示时应消费D公开内容摘要。
        // availableSeatCount只是查询快照，页面仍需在选座时刷新权威座位图。
        return new AlternativeShowResponse(
                Long.toString(show.showId()),
                Long.toString(show.movieId()),
                Long.toString(show.cinemaId()),
                toOffsetDateTime(show.startTime()),
                money(show.basePrice()),
                show.status(),
                show.availableSeatCount());
    }

    private String money(java.math.BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }
}
