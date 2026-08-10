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
        // 请求号和幂等键都必须带用户条件，防止不同账号之间复用订单操作记录。
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
        // 只有交易服务在事务中调用该方法；行锁用于支付、退款与取消之间的并发串行化。
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
    public List<OrderQuerySnapshot> findOrderQueryPage(OrderListCriteria criteria) {
        // 查询快照只用于列表展示，不把持久化行对象泄露给应用层。
        return mapper.findOrderQueryPage(criteria).stream()
                .map(this::toQuerySnapshot)
                .toList();
    }

    @Override
    public Optional<OrderQuerySnapshot> findOrderQueryByOrderNo(long userId, String orderNo) {
        return Optional.ofNullable(mapper.findOrderQueryByOrderNo(userId, orderNo))
                .map(this::toQuerySnapshot);
    }

    @Override
    public Optional<TravelOrderSummarySnapshot> findTravelOrderSummaryByIdAndUserId(
            long orderId,
            long userId) {
        return Optional.ofNullable(mapper.findTravelOrderSummaryByIdAndUserId(orderId, userId))
                .map(row -> new TravelOrderSummarySnapshot(
                        row.orderId(),
                        row.orderNo(),
                        row.showId(),
                        row.movieId(),
                        row.cinemaId(),
                        row.showStartTime()));
    }

    @Override
    public List<Long> findSeatIds(long orderId) {
        return mapper.findSeatIds(orderId);
    }

    @Override
    public List<OrderSeatReference> findSeatIdsByOrderIds(List<Long> orderIds) {
        // 空集合直接返回，避免生成无效 IN () SQL；批量读取供详情页把内部 seatId 映射为座位号。
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
        // 操作记录按 userId、动作和幂等键定位，参数哈希由上层校验重放请求是否一致。
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
        // 仅返回候选主键，过期任务随后用版本条件更新，避免批量任务覆盖并发支付。
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
    public List<RefundedTravelReconciliationCandidate> findRefundedTravelReconciliationCandidates(
            LocalDateTime refundedAtOrAfter,
            LocalDateTime refundedAtOrBefore,
            LocalDateTime afterRefundedAt,
            long afterOrderId,
            int limit) {
        return mapper.findRefundedTravelReconciliationCandidates(
                        refundedAtOrAfter,
                        refundedAtOrBefore,
                        afterRefundedAt,
                        afterOrderId,
                        limit)
                .stream()
                .map(row -> new RefundedTravelReconciliationCandidate(
                        row.orderId(),
                        row.userId(),
                        row.showId(),
                        row.orderVersion(),
                        row.refundedAt()))
                .toList();
    }

    @Override
    public void insertOrder(NewOrder order) {
        // 主表和座位快照分开写入，但调用方在同一事务中保证订单不可见半成品。
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
        // 保存行列快照，后续影院座位布局变化也不影响订单展示。
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
        // 幂等记录必须恰好插入一行；重复键由数据库约束交给上层处理。
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
        // 版本条件确保取消不会覆盖已经支付或退款中的新状态。
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
        // 数据库字符串状态在边界转换为领域枚举，未知值立即暴露而不是静默降级。
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

    private OrderQuerySnapshot toQuerySnapshot(OrderQuerySnapshotRow row) {
        // 列表查询使用独立快照，避免把更新用字段误用于只读展示。
        return new OrderQuerySnapshot(
                row.orderId(),
                row.orderNo(),
                row.userId(),
                row.showId(),
                row.movieId(),
                row.cinemaId(),
                row.showStartTime(),
                row.ticketCount(),
                row.unitPrice(),
                row.totalAmount(),
                OrderStatus.valueOf(row.status()),
                row.expireTime(),
                row.version(),
                row.updatedAt());
    }
}
