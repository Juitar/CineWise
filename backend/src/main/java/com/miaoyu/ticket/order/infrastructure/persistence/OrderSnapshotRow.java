package com.miaoyu.ticket.order.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 幂等查询和恢复查询使用的订单持久化投影。 */
public record OrderSnapshotRow(
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
        int version,
        LocalDateTime updatedAt) {
}
