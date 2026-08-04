package com.miaoyu.ticket.order.infrastructure;

import com.miaoyu.ticket.order.event.OrderInvalidated;
import com.miaoyu.ticket.order.event.OrderInvalidatedPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 将A的订单失效事件端口适配为Spring事务绑定事件。
 *
 * <p>适配器只登记事件，不调用D的应用服务。D在AFTER_COMMIT阶段开启自己的处理流程，
 * 因而出行任务取消失败不会被误并入A的退款事务。</p>
 */
@Component
public class SpringOrderInvalidatedPublisher implements OrderInvalidatedPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringOrderInvalidatedPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /** 事务回滚时Spring不会调用绑定到提交阶段的消费者。 */
    @Override
    public void publish(OrderInvalidated event) {
        applicationEventPublisher.publishEvent(event);
    }
}

