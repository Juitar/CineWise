package com.miaoyu.ticket.order.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Mock支付持久化投影。 */
public record PaymentSnapshotRow(
        long paymentId,
        String paymentNo,
        long orderId,
        String idempotencyKey,
        BigDecimal amount,
        String status,
        LocalDateTime requestTime,
        LocalDateTime paidTime,
        int version,
        LocalDateTime updatedAt) {
}
