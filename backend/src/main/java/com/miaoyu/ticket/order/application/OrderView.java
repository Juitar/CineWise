package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 建单与恢复查询共用的权威订单视图。 */
public record OrderView(
        long orderId,
        String orderNo,
        long showId,
        List<Long> seatIds,
        int ticketCount,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime expireTime,
        int stateVersion,
        LocalDateTime updatedAt) {
}
