package com.miaoyu.ticket.travel.infrastructure;

import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import com.miaoyu.ticket.travel.application.TravelTaskApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 将 A 的支付成功事件转交给 D 的应用服务。
 *
 * <p>监听器本身不碰 Mapper，且不启用无事务兜底：只有支付事务确实提交后才创建提醒任务。监听失败
 * 只记录可定位的订单和事件摘要，由 A 的 PAID 对账调用 {@code ensureTask} 补建，绝不影响支付结果。</p>
 */
@Component
public class TravelTaskPaymentEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TravelTaskPaymentEventListener.class);

    private final TravelTaskApplicationService travelTaskApplicationService;

    public TravelTaskPaymentEventListener(TravelTaskApplicationService travelTaskApplicationService) {
        this.travelTaskApplicationService = travelTaskApplicationService;
    }

    /** 仅在支付事务提交后进入 D 的独立事务，回滚支付不会留下任务。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        try {
            travelTaskApplicationService.ensureTask(event);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "支付后出行任务创建失败，等待订单对账补偿, orderId={}, eventId={}, errorType={}",
                    event.orderId(),
                    event.eventId(),
                    exception.getClass().getSimpleName(),
                    exception);
        }
    }
}
