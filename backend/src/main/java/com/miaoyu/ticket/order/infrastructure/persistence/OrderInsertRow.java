package com.miaoyu.ticket.order.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 创建待支付订单的最小持久化输入。 */
public record OrderInsertRow(
        long orderId,
        String orderNo,
        long userId,
        long showId,
        int ticketCount,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String status,
        LocalDateTime expireTime,
        String clientRequestId,
        String idempotencyKey,
        LocalDateTime createdAt) {
}
