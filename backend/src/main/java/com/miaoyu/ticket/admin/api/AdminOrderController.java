package com.miaoyu.ticket.admin.api;

import com.miaoyu.ticket.admin.application.AdminOrderListQuery;
import com.miaoyu.ticket.admin.application.AdminOrderPageView;
import com.miaoyu.ticket.admin.application.AdminOrderQueryService;
import com.miaoyu.ticket.admin.application.AdminOrderView;
import com.miaoyu.ticket.common.api.PageResult;
import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理订单REST适配层，只负责读取参数、调用应用服务和格式化公开DTO。
 *
 * <p>Controller不读取SecurityContext、不查询Mapper，也不解释用户关键字；
 * ADMIN复核、筛选规范化和批量聚合全部由AdminOrderQueryService完成。</p>
 *
 * <p>DTO转换遵循三个跨端规则：</p>
 * <ul>
 *   <li>雪花ID转换为十进制字符串，浏览器不得按JavaScript number解析；</li>
 *   <li>金额固定输出两位小数字符串，不让浮点数进入交易展示；</li>
 *   <li>本地业务时间附加Asia/Shanghai偏移量后再进入JSON。</li>
 * </ul>
 *
 * <p>本适配层没有更新、取消、补票或重试入口。管理页面发现异常后只能展示，
 * 真实交易修复仍必须进入对应的具名业务流程。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "管理订单")
@SecurityRequirement(name = "cookieAuth")
public class AdminOrderController {

    private final AdminOrderQueryService queryService;

    public AdminOrderController(AdminOrderQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 按白名单条件分页查询交易摘要。
     *
     * <p>userKeyword参数缺失表示不筛选；参数存在但为空白由应用层返回101001，
     * 从而保留“未填写”和“提交了非法空条件”的区别。</p>
     */
    @GetMapping
    @Operation(summary = "分页查询管理订单")
    public Result<PageResult<AdminOrderSummaryResponse>> queryOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String userKeyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String movieId,
            @RequestParam(required = false) String showId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        AdminOrderPageView result = queryService.queryOrders(new AdminOrderListQuery(
                orderNo,
                userKeyword,
                status,
                movieId,
                showId,
                dateFrom,
                dateTo,
                page,
                size));
        return Result.success(new PageResult<>(
                result.total(),
                result.page(),
                result.size(),
                result.records().stream().map(this::toSummaryResponse).toList()));
    }

    /** 详情只读聚合订单座位、支付、电子票和退款，不提供任何状态修改入口。 */
    @GetMapping("/{orderNo}")
    @Operation(summary = "查询管理订单详情")
    public Result<AdminOrderDetailResponse> queryOrder(
            @PathVariable
            @NotBlank
            @Size(max = 32)
            String orderNo) {
        return Result.success(toDetailResponse(queryService.queryOrder(orderNo)));
    }

    /**
     * 将应用层聚合压缩为列表最小摘要。
     *
     * <p>列表只暴露关联状态，不复制详情中的支付号、票号和退款原因。关联不存在时
     * 使用null表达“尚未产生记录”，不能伪造成失败或未知状态字符串。</p>
     *
     * <p>emailMasked由C产生并原样透传；A不检查邮箱结构，也不尝试从其他字段补全。</p>
     */
    private AdminOrderSummaryResponse toSummaryResponse(AdminOrderView order) {
        return new AdminOrderSummaryResponse(
                Long.toString(order.orderId()),
                order.orderNo(),
                Long.toString(order.userId()),
                order.emailMasked(),
                Long.toString(order.showId()),
                Long.toString(order.movieId()),
                Long.toString(order.cinemaId()),
                toOffsetDateTime(order.showStartTime()),
                order.ticketCount(),
                money(order.unitPrice()),
                money(order.totalAmount()),
                order.orderStatus().name(),
                toOffsetDateTime(order.expireTime()),
                order.payment() == null ? null : order.payment().status().name(),
                order.ticket() == null ? null : order.ticket().status().name(),
                order.refund() == null ? null : order.refund().status().name(),
                order.stateVersion(),
                toOffsetDateTime(order.createdAt()),
                toOffsetDateTime(order.updatedAt()));
    }

    /**
     * 组装详情响应，并继续复用列表摘要的金额、ID和时间格式。
     *
     * <p>座位使用订单创建时的行列快照，不用当前show_seat覆盖历史信息。支付、票和
     * 退款为空时对应JSON字段省略，但summary始终存在。</p>
     *
     * <p>该方法只转换内存视图，不查询Repository，因此序列化阶段不会形成N+1。</p>
     */
    private AdminOrderDetailResponse toDetailResponse(AdminOrderView order) {
        return new AdminOrderDetailResponse(
                toSummaryResponse(order),
                toOffsetDateTime(order.paidTime()),
                toOffsetDateTime(order.cancelledTime()),
                toOffsetDateTime(order.refundedTime()),
                order.seats().stream()
                        .map(seat -> new AdminOrderDetailResponse.AdminSeatResponse(
                                Long.toString(seat.seatId()),
                                seat.rowNo(),
                                seat.seatNo(),
                                money(seat.unitPrice())))
                        .toList(),
                toPaymentResponse(order.payment()),
                toTicketResponse(order.ticket()),
                toRefundResponse(order.refund()));
    }

    /**
     * 支付详情仅包含管理员核对成功链路需要的公开字段。
     *
     * <p>幂等键和请求秘密在Repository选择列阶段已经排除，Controller无机会误回传。</p>
     */
    private AdminOrderDetailResponse.AdminPaymentResponse toPaymentResponse(AdminOrderView.PaymentView payment) {
        if (payment == null) {
            return null;
        }
        return new AdminOrderDetailResponse.AdminPaymentResponse(
                payment.paymentNo(),
                money(payment.amount()),
                payment.status().name(),
                toOffsetDateTime(payment.requestedAt()),
                toOffsetDateTime(payment.paidAt()),
                payment.stateVersion(),
                toOffsetDateTime(payment.updatedAt()));
    }

    /**
     * 电子票详情刻意不包含二维码载荷。
     *
     * <p>管理员可以看到票号和状态用于排障，但不能通过管理接口取得可直接验票的凭据。</p>
     */
    private AdminOrderDetailResponse.AdminTicketResponse toTicketResponse(AdminOrderView.TicketView ticket) {
        if (ticket == null) {
            return null;
        }
        return new AdminOrderDetailResponse.AdminTicketResponse(
                ticket.ticketCode(),
                ticket.status().name(),
                toOffsetDateTime(ticket.issuedAt()),
                toOffsetDateTime(ticket.invalidatedAt()),
                ticket.stateVersion(),
                toOffsetDateTime(ticket.updatedAt()));
    }

    /**
     * 退款详情只展示公开原因、状态和处理时间。
     *
     * <p>影响快照、Agent actionId和退款幂等键属于内部恢复信息，不能进入响应DTO。</p>
     */
    private AdminOrderDetailResponse.AdminRefundResponse toRefundResponse(AdminOrderView.RefundView refund) {
        if (refund == null) {
            return null;
        }
        return new AdminOrderDetailResponse.AdminRefundResponse(
                refund.refundNo(),
                refund.reason(),
                refund.status().name(),
                toOffsetDateTime(refund.requestedAt()),
                toOffsetDateTime(refund.processedAt()),
                refund.stateVersion(),
                toOffsetDateTime(refund.updatedAt()));
    }

    /**
     * 使用UNNECESSARY验证金额已经符合两位精度。
     *
     * <p>若上游意外产生更高精度，响应应失败并暴露内部问题，不能在管理页面静默舍入后
     * 展示与权威订单不同的金额。</p>
     */
    private String money(BigDecimal value) {
        // 数据库金额已由约束保证两位精度；UNNECESSARY可阻止响应层偷偷四舍五入。
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    /**
     * 将数据库LocalDateTime解释为冻结的业务时区。
     *
     * <p>可空交易时间保持null；禁止用当前时间或零值补齐尚未发生的支付、取消或退款。</p>
     */
    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }
}
