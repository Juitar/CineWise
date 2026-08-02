package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.common.error.BusinessException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 统一处理建单的同键同参恢复和同键异参拒绝。 */
@Service
public class OrderIdempotencyService {

    private final OrderRepository repository;

    public OrderIdempotencyService(OrderRepository repository) {
        this.repository = repository;
    }

    /** 两个唯一请求键必须同时指向同一订单且业务参数一致。 */
    @Transactional(readOnly = true)
    public Optional<OrderView> findMatchingOrder(long userId, CreateOrderCommand command) {
        List<OrderRepository.OrderSnapshot> matches = repository.findByRequestKeys(
                userId,
                command.clientRequestId(),
                command.idempotencyKey());
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        long firstOrderId = matches.getFirst().orderId();
        boolean multipleOrdersMatched = matches.stream()
                .anyMatch(order -> order.orderId() != firstOrderId);
        if (multipleOrdersMatched) {
            throw mismatch();
        }

        OrderRepository.OrderSnapshot existing = matches.getFirst();
        List<Long> existingSeatIds = repository.findSeatIds(existing.orderId());
        if (!matchesCommand(existing, existingSeatIds, command)) {
            throw mismatch();
        }
        return Optional.of(toView(existing, existingSeatIds));
    }

    /** 响应丢失时只按当前用户恢复，避免泄露其他用户订单。 */
    @Transactional(readOnly = true)
    public Optional<OrderView> findByClientRequestId(long userId, String clientRequestId) {
        return repository.findByClientRequestId(userId, clientRequestId)
                .map(order -> toView(order, repository.findSeatIds(order.orderId())));
    }

    private boolean matchesCommand(
            OrderRepository.OrderSnapshot existing,
            List<Long> existingSeatIds,
            CreateOrderCommand command) {
        return existing.showId() == command.showId()
                && Objects.equals(existing.clientRequestId(), command.clientRequestId())
                && Objects.equals(existing.idempotencyKey(), command.idempotencyKey())
                && existingSeatIds.equals(command.seatIds());
    }

    private OrderView toView(OrderRepository.OrderSnapshot order, List<Long> seatIds) {
        return new OrderView(
                order.orderId(),
                order.orderNo(),
                order.showId(),
                List.copyOf(seatIds),
                order.ticketCount(),
                order.unitPrice(),
                order.totalAmount(),
                order.status(),
                order.expireTime(),
                order.version(),
                order.updatedAt());
    }

    private BusinessException mismatch() {
        return new BusinessException(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH);
    }
}
