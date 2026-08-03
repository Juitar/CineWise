package com.miaoyu.ticket.order.event;

/** 支付事务提交后的事件发布端口；D区域摘要未就绪前不提供伪造实现。 */
@FunctionalInterface
public interface PaymentSucceededEventPublisher {

    void publish(PaymentSucceededEvent event);
}
