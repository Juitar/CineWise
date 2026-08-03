package com.miaoyu.ticket.order.domain;

/** 电子票权威状态，退款或异常作废只能经具名交易用例迁移。 */
public enum ElectronicTicketStatus {
    VALID,
    REFUNDED,
    INVALIDATED
}
