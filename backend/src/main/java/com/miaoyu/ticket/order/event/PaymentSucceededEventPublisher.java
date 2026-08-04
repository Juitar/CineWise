package com.miaoyu.ticket.order.event;

/** 支付事务内的事件登记端口；消费者必须绑定AFTER_COMMIT阶段。 */
@FunctionalInterface
public interface PaymentSucceededEventPublisher {

    void publish(PaymentSucceededEvent event);
}
