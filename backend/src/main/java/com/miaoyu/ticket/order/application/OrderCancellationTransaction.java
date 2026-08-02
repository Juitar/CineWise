package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.order.domain.OrderOperationType;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.ticketing.application.SeatReleaseService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将订单取消、幂等结果和座位释放放在同一本地事务。 */
@Service
public class OrderCancellationTransaction {

    private static final OrderOperationType ACTION = OrderOperationType.CANCEL;

    private final OrderRepository repository;
    private final OrderViewFactory viewFactory;
    private final SeatReleaseService seatReleaseService;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public OrderCancellationTransaction(
            OrderRepository repository,
            OrderViewFactory viewFactory,
            SeatReleaseService seatReleaseService,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.repository = repository;
        this.viewFactory = viewFactory;
        this.seatReleaseService = seatReleaseService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** 先锁定本人订单行，使取消、过期和支付对同一订单串行决定终态。 */
    @Transactional
    public OrderView cancel(long userId, String orderNo, String idempotencyKey) {
        OrderRepository.OrderSnapshot order = repository.findByOrderNoForUpdate(userId, orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        String parameterHash = OrderOperationParameterHasher.hash(ACTION, order.orderId());
        OrderRepository.OrderOperationSnapshot existing = repository.findOperation(
                        userId,
                        ACTION,
                        idempotencyKey)
                .orElse(null);
        if (existing != null) {
            validateExistingOperation(existing, parameterHash);
            return viewFactory.create(order);
        }
        if (order.status() == OrderStatus.CANCELLED) {
            insertOperation(order, idempotencyKey, parameterHash, order.updatedAt());
            return viewFactory.create(order);
        }
        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(OrderErrorCode.ORDER_STATE_CONFLICT);
        }

        LocalDateTime cancelledAt = LocalDateTime.ofInstant(
                clock.instant(),
                ClockConfiguration.BUSINESS_ZONE_ID);
        if (!repository.cancelOrder(order.orderId(), order.version(), cancelledAt)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATE_CONFLICT);
        }
        int releasedSeatCount = seatReleaseService.releaseLockedSeats(order.orderNo(), cancelledAt);
        requireAllSeatsReleased(order, releasedSeatCount);
        OrderRepository.OrderSnapshot cancelled = repository.findById(order.orderId())
                .orElseThrow(() -> new IllegalStateException("取消后订单丢失"));
        insertOperation(cancelled, idempotencyKey, parameterHash, cancelledAt);
        return viewFactory.create(cancelled);
    }

    private void validateExistingOperation(
            OrderRepository.OrderOperationSnapshot existing,
            String parameterHash) {
        if (!Objects.equals(existing.parameterHash(), parameterHash)) {
            throw new BusinessException(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH);
        }
    }

    private void insertOperation(
            OrderRepository.OrderSnapshot order,
            String idempotencyKey,
            String parameterHash,
            LocalDateTime createdAt) {
        repository.insertOperation(new OrderRepository.NewOrderOperation(
                idGenerator.nextId(),
                order.userId(),
                ACTION,
                idempotencyKey,
                order.orderId(),
                order.orderNo(),
                parameterHash,
                order.status(),
                order.version(),
                createdAt));
    }

    private void requireAllSeatsReleased(
            OrderRepository.OrderSnapshot order,
            int releasedSeatCount) {
        if (releasedSeatCount != order.ticketCount()) {
            throw new IllegalStateException("取消订单的座位归属不完整，已回滚");
        }
    }
}
