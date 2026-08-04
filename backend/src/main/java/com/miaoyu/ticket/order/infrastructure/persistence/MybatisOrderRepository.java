package com.miaoyu.ticket.order.infrastructure.persistence;

import com.miaoyu.ticket.order.application.OrderRepository;
import com.miaoyu.ticket.order.domain.OrderOperationType;
import com.miaoyu.ticket.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 将订单持久化投影显式映射为应用层类型。 */
@Repository
public class MybatisOrderRepository implements OrderRepository {

    private final OrderPersistenceMapper mapper;

    public MybatisOrderRepository(OrderPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<OrderSnapshot> findByRequestKeys(
            long userId,
            String clientRequestId,
            String idempotencyKey) {
        return mapper.findByRequestKeys(userId, clientRequestId, idempotencyKey).stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public Optional<OrderSnapshot> findByClientRequestId(long userId, String clientRequestId) {
        return Optional.ofNullable(mapper.findByClientRequestId(userId, clientRequestId))
                .map(this::toSnapshot);
    }

    @Override
    public Optional<OrderSnapshot> findByOrderNo(long userId, String orderNo) {
        return Optional.ofNullable(mapper.findByOrderNo(userId, orderNo))
                .map(this::toSnapshot);
    }

    @Override
    public Optional<OrderSnapshot> findByOrderNoForUpdate(long userId, String orderNo) {
        return Optional.ofNullable(mapper.findByOrderNoForUpdate(userId, orderNo))
                .map(this::toSnapshot);
    }

    @Override
    public Optional<OrderSnapshot> findById(long orderId) {
        return Optional.ofNullable(mapper.findById(orderId))
                .map(this::toSnapshot);
    }

    @Override
    public long countOrders(OrderListCriteria criteria) {
        return mapper.countOrders(criteria);
    }

    @Override
    public List<OrderSnapshot> findOrderPage(OrderListCriteria criteria) {
        return mapper.findOrderPage(criteria).stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public List<Long> findSeatIds(long orderId) {
        return mapper.findSeatIds(orderId);
    }

    @Override
    public List<OrderSeatReference> findSeatIdsByOrderIds(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return mapper.findSeatIdsByOrderIds(orderIds).stream()
                .map(row -> new OrderSeatReference(row.orderId(), row.seatId()))
                .toList();
    }

    @Override
    public Optional<OrderOperationSnapshot> findOperation(
            long userId,
            OrderOperationType action,
            String idempotencyKey) {
        return Optional.ofNullable(mapper.findOperation(userId, action.name(), idempotencyKey))
                .map(row -> new OrderOperationSnapshot(
                        row.id(),
                        row.userId(),
                        OrderOperationType.valueOf(row.action()),
                        row.idempotencyKey(),
                        row.orderId(),
                        row.orderNoSnapshot(),
                        row.parameterHash(),
                        OrderStatus.valueOf(row.resultStatus()),
                        row.resultVersion(),
                        row.createdAt()));
    }

    @Override
    public List<Long> findExpiredCandidateIds(LocalDateTime expiresAtOrBefore, int limit) {
        return mapper.findExpiredCandidateIds(expiresAtOrBefore, limit);
    }

    @Override
    public List<PaidTravelReconciliationCandidate> findPaidTravelReconciliationCandidates(
            LocalDateTime paidAtOrAfter,
            LocalDateTime paidAtOrBefore,
            LocalDateTime afterPaidAt,
            long afterOrderId,
            int limit) {
        return mapper.findPaidTravelReconciliationCandidates(
                        paidAtOrAfter,
                        paidAtOrBefore,
                        afterPaidAt,
                        afterOrderId,
                        limit)
                .stream()
                .map(row -> new PaidTravelReconciliationCandidate(
                        row.orderId(),
                        row.userId(),
                        row.showId(),
                        row.orderVersion(),
                        row.paidAt()))
                .toList();
    }

    @Override
    public void insertOrder(NewOrder order) {
        int inserted = mapper.insertOrder(new OrderInsertRow(
                order.orderId(),
                order.orderNo(),
                order.userId(),
                order.showId(),
                order.ticketCount(),
                order.unitPrice(),
                order.totalAmount(),
                order.status().name(),
                order.expireTime(),
                order.clientRequestId(),
                order.idempotencyKey(),
                order.createdAt()));
        if (inserted != 1) {
            throw new IllegalStateException("订单主表写入行数异常");
        }
    }

    @Override
    public void insertOrderSeat(NewOrderSeat orderSeat) {
        int inserted = mapper.insertOrderSeat(new OrderSeatInsertRow(
                orderSeat.id(),
                orderSeat.orderId(),
                orderSeat.showSeatId(),
                orderSeat.rowNoSnapshot(),
                orderSeat.seatNoSnapshot(),
                orderSeat.unitPrice(),
                orderSeat.createdAt()));
        if (inserted != 1) {
            throw new IllegalStateException("订单座位快照写入行数异常");
        }
    }

    @Override
    public void insertOperation(NewOrderOperation operation) {
        int inserted = mapper.insertOperation(new OrderOperationInsertRow(
                operation.id(),
                operation.userId(),
                operation.action().name(),
                operation.idempotencyKey(),
                operation.orderId(),
                operation.orderNoSnapshot(),
                operation.parameterHash(),
                operation.resultStatus().name(),
                operation.resultVersion(),
                operation.createdAt()));
        if (inserted != 1) {
            throw new IllegalStateException("订单操作幂等记录写入行数异常");
        }
    }

    @Override
    public boolean cancelOrder(long orderId, int expectedVersion, LocalDateTime cancelledAt) {
        return mapper.cancelOrder(orderId, expectedVersion, cancelledAt) == 1;
    }

    @Override
    public boolean expireOrder(
            long orderId,
            int expectedVersion,
            LocalDateTime expiresAtOrBefore,
            LocalDateTime updatedAt) {
        return mapper.expireOrder(orderId, expectedVersion, expiresAtOrBefore, updatedAt) == 1;
    }

    @Override
    public boolean markOrderPaying(
            long orderId,
            int expectedVersion,
            LocalDateTime paidBefore,
            LocalDateTime updatedAt) {
        return mapper.markOrderPaying(orderId, expectedVersion, paidBefore, updatedAt) == 1;
    }

    @Override
    public boolean markOrderPaid(long orderId, int expectedVersion, LocalDateTime paidAt) {
        return mapper.markOrderPaid(orderId, expectedVersion, paidAt) == 1;
    }

    @Override
    public boolean markOrderRefunding(long orderId, int expectedVersion, LocalDateTime updatedAt) {
        return mapper.markOrderRefunding(orderId, expectedVersion, updatedAt) == 1;
    }

    @Override
    public boolean markOrderRefunded(long orderId, int expectedVersion, LocalDateTime refundedAt) {
        return mapper.markOrderRefunded(orderId, expectedVersion, refundedAt) == 1;
    }

    private OrderSnapshot toSnapshot(OrderSnapshotRow row) {
        return new OrderSnapshot(
                row.orderId(),
                row.orderNo(),
                row.userId(),
                row.showId(),
                row.ticketCount(),
                row.unitPrice(),
                row.totalAmount(),
                OrderStatus.valueOf(row.status()),
                row.expireTime(),
                row.clientRequestId(),
                row.idempotencyKey(),
                row.version(),
                row.updatedAt());
    }
}
