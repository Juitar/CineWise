package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.RefundStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款记录与订单、电子票最新终态的聚合视图。
 *
 * <ul>
 *   <li>refundStatus说明唯一退款记录的处理终态；</li>
 *   <li>orderStatus和ticketStatus使客户端无需拼接多个写结果；</li>
 *   <li>refundAmount取创建退款时的权威快照；</li>
 *   <li>stateVersion只用于丢弃旧响应，不授权客户端发起状态迁移。</li>
 * </ul>
 */
public record RefundView(
        long refundId,
        String refundNo,
        long orderId,
        String orderNo,
        RefundStatus refundStatus,
        BigDecimal refundAmount,
        OrderStatus orderStatus,
        ElectronicTicketStatus ticketStatus,
        int stateVersion,
        LocalDateTime updatedAt) {
}
