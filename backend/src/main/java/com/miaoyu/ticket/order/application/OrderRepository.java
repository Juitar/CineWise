package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.OrderOperationType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 订单权威持久化端口；请求键查询始终限定当前用户。 */
public interface OrderRepository {

    /** 按客户端请求标识或幂等键查询，最多返回两个唯一约束命中。 */
    List<OrderSnapshot> findByRequestKeys(long userId, String clientRequestId, String idempotencyKey);

    /** 按当前用户与客户端请求标识恢复订单。 */
    Optional<OrderSnapshot> findByClientRequestId(long userId, String clientRequestId);

    /** 按本人订单号查询，不向调用方暴露其他用户资源。 */
    Optional<OrderSnapshot> findByOrderNo(long userId, String orderNo);

    /** 取消事务使用的本人订单行锁。 */
    Optional<OrderSnapshot> findByOrderNoForUpdate(long userId, String orderNo);

    Optional<OrderSnapshot> findById(long orderId);

    long countOrders(OrderListCriteria criteria);

    List<OrderSnapshot> findOrderPage(OrderListCriteria criteria);

    /** 按主键查询订单座位快照，并按座位ID升序返回。 */
    List<Long> findSeatIds(long orderId);

    /** 批量查询当前页订单座位，避免列表N+1查询。 */
    List<OrderSeatReference> findSeatIdsByOrderIds(List<Long> orderIds);

    Optional<OrderOperationSnapshot> findOperation(
            long userId,
            OrderOperationType action,
            String idempotencyKey);

    List<Long> findExpiredCandidateIds(LocalDateTime expiresAtOrBefore, int limit);

    void insertOrder(NewOrder order);

    void insertOrderSeat(NewOrderSeat orderSeat);

    void insertOperation(NewOrderOperation operation);

    boolean cancelOrder(long orderId, int expectedVersion, LocalDateTime cancelledAt);

    boolean expireOrder(
            long orderId,
            int expectedVersion,
            LocalDateTime expiresAtOrBefore,
            LocalDateTime updatedAt);

    record OrderListCriteria(
            long userId,
            String orderNo,
            OrderStatus status,
            LocalDateTime createdAtOrAfter,
            LocalDateTime createdBefore,
            int offset,
            int limit) {
    }

    record OrderSeatReference(long orderId, long seatId) {
    }

    record OrderOperationSnapshot(
            long id,
            long userId,
            OrderOperationType action,
            String idempotencyKey,
            long orderId,
            String orderNoSnapshot,
            String parameterHash,
            OrderStatus resultStatus,
            int resultVersion,
            LocalDateTime createdAt) {
    }

    record OrderSnapshot(
            long orderId,
            String orderNo,
            long userId,
            long showId,
            int ticketCount,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            OrderStatus status,
            LocalDateTime expireTime,
            String clientRequestId,
            String idempotencyKey,
            int version,
            LocalDateTime updatedAt) {
    }

    record NewOrder(
            long orderId,
            String orderNo,
            long userId,
            long showId,
            int ticketCount,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            OrderStatus status,
            LocalDateTime expireTime,
            String clientRequestId,
            String idempotencyKey,
            LocalDateTime createdAt) {
    }

    record NewOrderSeat(
            long id,
            long orderId,
            long showSeatId,
            String rowNoSnapshot,
            String seatNoSnapshot,
            BigDecimal unitPrice,
            LocalDateTime createdAt) {
    }

    record NewOrderOperation(
            long id,
            long userId,
            OrderOperationType action,
            String idempotencyKey,
            long orderId,
            String orderNoSnapshot,
            String parameterHash,
            OrderStatus resultStatus,
            int resultVersion,
            LocalDateTime createdAt) {
    }
}
