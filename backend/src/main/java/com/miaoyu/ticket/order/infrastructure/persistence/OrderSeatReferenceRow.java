package com.miaoyu.ticket.order.infrastructure.persistence;

/** 订单列表批量加载座位快照的最小投影。 */
public record OrderSeatReferenceRow(long orderId, long seatId) {
}
