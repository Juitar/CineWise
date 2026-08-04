package com.miaoyu.ticket.order.event;

/** 退款事务内的订单失效事件登记端口；消费者必须绑定AFTER_COMMIT阶段。 */
@FunctionalInterface
public interface OrderInvalidatedPublisher {

    void publish(OrderInvalidated event);
}
