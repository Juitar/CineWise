package com.miaoyu.ticket.order.infrastructure;

import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import com.miaoyu.ticket.order.event.PaymentSucceededEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 将A的稳定事件端口适配为Spring事务绑定事件，不暴露框架给交易领域契约。
 *
 * <p>适配器只负责向Spring登记事件，不调用D的应用服务。D通过AFTER_COMMIT监听器开启自己的独立事务，
 * 因而提醒写入不会被误并入A的支付事务。</p>
 */
@Component
public class SpringPaymentSucceededEventPublisher implements PaymentSucceededEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringPaymentSucceededEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 调用发生在支付事务内，使D的AFTER_COMMIT监听器只在成功提交后执行。
     * 若原事务回滚，Spring不会执行绑定到提交阶段的消费者。
     */
    @Override
    public void publish(PaymentSucceededEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
