package com.miaoyu.ticket.admin.application;

import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.PaymentStatus;
import com.miaoyu.ticket.order.domain.RefundStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端只读交易聚合，不承载任何状态迁移能力。
 *
 * <p>列表使用同一类型但不加载座位明细；详情才填充seats。
 * 支付、电子票和退款允许为空，因为待支付、取消或过期订单可能尚未产生这些聚合。</p>
 *
 * <p>视图刻意排除clientRequestId、幂等键、二维码载荷、退款影响快照和actionId，
 * 避免这些内部恢复或确认凭证穿透到REST层。</p>
 *
 * <p>emailMasked允许为空只表示对应历史用户摘要缺失；它不影响订单、支付、票和退款
 * 的可信状态。关联交易视图为空表示该记录尚未产生，而不是查询失败。</p>
 *
 * <p>所有集合在构造时复制，确保一次只读事务形成的查询快照不会被Controller修改。</p>
 */
public record AdminOrderView(
        long orderId,
        String orderNo,
        long userId,
        String emailMasked,
        long showId,
        long movieId,
        long cinemaId,
        LocalDateTime showStartTime,
        int ticketCount,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        OrderStatus orderStatus,
        LocalDateTime expireTime,
        LocalDateTime paidTime,
        LocalDateTime cancelledTime,
        LocalDateTime refundedTime,
        int stateVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<SeatView> seats,
        PaymentView payment,
        TicketView ticket,
        RefundView refund) {

    public AdminOrderView {
        // 座位快照属于查询结果的一部分，禁止响应组装阶段继续追加。
        seats = List.copyOf(seats);
    }

    /**
     * 订单创建时固化的座位行列和单价。
     *
     * <p>该结构不读取后来可能变化的座位状态、锁归属或版本。</p>
     */
    public record SeatView(
            long seatId,
            String rowNo,
            String seatNo,
            BigDecimal unitPrice) {
    }

    /**
     * 支付摘要不包含幂等键，只暴露管理员排障所需的状态和时间。
     *
     * <p>stateVersion帮助前端丢弃旧响应，但不能作为更新支付的授权凭证。</p>
     */
    public record PaymentView(
            String paymentNo,
            BigDecimal amount,
            PaymentStatus status,
            LocalDateTime requestedAt,
            LocalDateTime paidAt,
            int stateVersion,
            LocalDateTime updatedAt) {
    }

    /**
     * 电子票摘要不包含qrPayload，防止管理查询成为可用票码泄露入口。
     *
     * <p>票号可以展示，真正验票载荷始终留在受用户归属保护的票务流程中。</p>
     */
    public record TicketView(
            String ticketCode,
            ElectronicTicketStatus status,
            LocalDateTime issuedAt,
            LocalDateTime invalidatedAt,
            int stateVersion,
            LocalDateTime updatedAt) {
    }

    /**
     * 退款摘要排除impactSnapshot、actionId和幂等键等内部字段。
     *
     * <p>管理页面只用于解释现状，不复用这些字段再次触发退款。</p>
     */
    public record RefundView(
            String refundNo,
            String reason,
            RefundStatus status,
            LocalDateTime requestedAt,
            LocalDateTime processedAt,
            int stateVersion,
            LocalDateTime updatedAt) {
    }
}
