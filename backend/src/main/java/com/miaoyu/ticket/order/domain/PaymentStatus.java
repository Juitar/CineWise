package com.miaoyu.ticket.order.domain;

/** Mock支付权威状态，支付成功事务不持久化不确定失败。 */
public enum PaymentStatus {
    INITIALIZED,
    PROCESSING,
    SUCCESS
}
