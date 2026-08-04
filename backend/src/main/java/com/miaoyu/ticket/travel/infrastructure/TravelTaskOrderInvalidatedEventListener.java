package com.miaoyu.ticket.travel.infrastructure;

import com.miaoyu.ticket.order.event.OrderInvalidated;
import com.miaoyu.ticket.travel.application.TravelTaskApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 将 A 的退款完成事件转交给 D 的取消用例。
 *
 * <p>只在退款事务提交后执行，监听失败不会回滚退款；A 的 REFUNDED 对账会调用同一个公开应用服务补偿。</p>
 */
@Component
public class TravelTaskOrderInvalidatedEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TravelTaskOrderInvalidatedEventListener.class);

    private final TravelTaskApplicationService travelTaskApplicationService;

    public TravelTaskOrderInvalidatedEventListener(TravelTaskApplicationService travelTaskApplicationService) {
        this.travelTaskApplicationService = travelTaskApplicationService;
    }

    /** 退款确实提交后才取消提醒，回滚退款不能留下 CANCELLED 墓碑。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderInvalidated(OrderInvalidated event) {
        try {
            travelTaskApplicationService.ensureTaskCancelled(event);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "退款后出行任务取消失败，等待退款订单对账补偿, orderId={}, eventId={}, errorType={}",
                    event.orderId(),
                    event.eventId(),
                    exception.getClass().getSimpleName(),
                    exception);
        }
    }
}
