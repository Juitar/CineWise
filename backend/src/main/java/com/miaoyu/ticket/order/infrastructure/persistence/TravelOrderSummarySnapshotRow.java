package com.miaoyu.ticket.order.infrastructure.persistence;

import java.time.LocalDateTime;

/** 订单详情跨模块端口使用的最小数据库行，不复用交易快照。 */
public record TravelOrderSummarySnapshotRow(
        long orderId,
        String orderNo,
        long showId,
        long movieId,
        long cinemaId,
        LocalDateTime showStartTime) {
}
