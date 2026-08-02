package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.ticketing.application.SeatReleaseService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 一个候选订单的独立过期事务。 */
@Service
public class OrderExpiryTransaction {

    private final OrderRepository repository;
    private final SeatReleaseService seatReleaseService;

    public OrderExpiryTransaction(
            OrderRepository repository,
            SeatReleaseService seatReleaseService) {
        this.repository = repository;
        this.seatReleaseService = seatReleaseService;
    }

    /** 条件失败表示其他取消、过期或支付流程已经取得权威结果。 */
    @Transactional
    public boolean expire(long orderId, LocalDateTime now) {
        OrderRepository.OrderSnapshot order = repository.findById(orderId)
                .orElse(null);
        if (order == null
                || order.status() != OrderStatus.PENDING_PAYMENT
                || order.expireTime().isAfter(now)) {
            return false;
        }
        if (!repository.expireOrder(orderId, order.version(), now, now)) {
            return false;
        }
        int releasedSeatCount = seatReleaseService.releaseLockedSeats(order.orderNo(), now);
        if (releasedSeatCount != order.ticketCount()) {
            throw new IllegalStateException("过期订单的座位归属不完整，已回滚");
        }
        return true;
    }
}
