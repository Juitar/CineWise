package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.PaymentStatus;
import java.time.LocalDateTime;

/** 支付写入和只读恢复共享的权威结果。 */
public record PaymentView(
        long orderId,
        String orderNo,
        String paymentNo,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        Long ticketId,
        int stateVersion,
        LocalDateTime updatedAt) {
}
