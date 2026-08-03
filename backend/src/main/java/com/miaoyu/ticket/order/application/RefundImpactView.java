package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退票前展示的权威影响摘要。
 *
 * <ul>
 *   <li>refundAmount来自已支付订单，不接受前端传入金额；</li>
 *   <li>订单和票版本用于页面判断影响摘要是否已经过期；</li>
 *   <li>showStartTime明确展示退票截止边界；</li>
 *   <li>impactText只描述后果，不代表已经执行退票。</li>
 * </ul>
 * <p>查询本身不创建确认记录，也不占用退款幂等键。</p>
 */
public record RefundImpactView(
        long orderId,
        String orderNo,
        BigDecimal refundAmount,
        OrderStatus orderStatus,
        ElectronicTicketStatus ticketStatus,
        LocalDateTime showStartTime,
        int orderVersion,
        int ticketVersion,
        String impactText) {
}
