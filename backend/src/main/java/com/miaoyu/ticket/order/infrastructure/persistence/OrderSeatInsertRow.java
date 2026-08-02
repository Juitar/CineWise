package com.miaoyu.ticket.order.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单座位的追加写快照。 */
public record OrderSeatInsertRow(
        long id,
        long orderId,
        long showSeatId,
        String rowNoSnapshot,
        String seatNoSnapshot,
        BigDecimal unitPrice,
        LocalDateTime createdAt) {
}
