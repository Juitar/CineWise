package com.miaoyu.ticket.order.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 新建PROCESSING Mock支付的持久化参数。 */
public record PaymentInsertRow(
        long paymentId,
        String paymentNo,
        long orderId,
        String idempotencyKey,
        BigDecimal amount,
        LocalDateTime requestedAt) {
}
