package com.miaoyu.ticket.order.domain;

/** 订单权威状态，状态迁移只能由具名应用用例执行。 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAYING,
    PAID,
    CANCELLED,
    EXPIRED,
    REFUNDING,
    REFUNDED
}
