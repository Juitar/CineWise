package com.miaoyu.ticket.order.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 个人订单列表与详情连接movie_show后的只读持久化投影。 */
public record OrderQuerySnapshotRow(
        long orderId,
        String orderNo,
        long userId,
        long showId,
        long movieId,
        long cinemaId,
        LocalDateTime showStartTime,
        int ticketCount,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String status,
        LocalDateTime expireTime,
        int version,
        LocalDateTime updatedAt) {
}
