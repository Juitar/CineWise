package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.OrderOperationType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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

    /** 个人订单列表使用的只读场次投影，不参与交易锁定。 */
    List<OrderQuerySnapshot> findOrderQueryPage(OrderListCriteria criteria);

    /** 按本人订单号读取订单与场次上下文，不替代交易状态查询。 */
    Optional<OrderQuerySnapshot> findOrderQueryByOrderNo(long userId, String orderNo);

    /** 按主键查询订单座位快照，并按座位ID升序返回。 */
    List<Long> findSeatIds(long orderId);

    /** 批量查询当前页订单座位，避免列表N+1查询。 */
    List<OrderSeatReference> findSeatIdsByOrderIds(List<Long> orderIds);

    Optional<OrderOperationSnapshot> findOperation(
            long userId,
            OrderOperationType action,
            String idempotencyKey);

    List<Long> findExpiredCandidateIds(LocalDateTime expiresAtOrBefore, int limit);

    /**
     * 按原始支付时间和订单ID做稳定键集分页，只返回冻结窗口内仍为PAID的候选。
     * 候选只用于缩小扫描范围，跨模块调用前必须再次读取订单权威状态。
     */
    List<PaidTravelReconciliationCandidate> findPaidTravelReconciliationCandidates(
            LocalDateTime paidAtOrAfter,
            LocalDateTime paidAtOrBefore,
            LocalDateTime afterPaidAt,
            long afterOrderId,
            int limit);

    /**
     * 按退款完成时间和订单ID稳定分页，只返回冻结窗口内仍为REFUNDED的候选。
     * 候选不替代权威状态，调用D前仍须按订单主键重读状态与版本。
     */
    List<RefundedTravelReconciliationCandidate> findRefundedTravelReconciliationCandidates(
            LocalDateTime refundedAtOrAfter,
            LocalDateTime refundedAtOrBefore,
            LocalDateTime afterRefundedAt,
            long afterOrderId,
            int limit);

    void insertOrder(NewOrder order);

    void insertOrderSeat(NewOrderSeat orderSeat);

    void insertOperation(NewOrderOperation operation);

    boolean cancelOrder(long orderId, int expectedVersion, LocalDateTime cancelledAt);

    boolean expireOrder(
            long orderId,
            int expectedVersion,
            LocalDateTime expiresAtOrBefore,
            LocalDateTime updatedAt);

    /** 只有仍未过期的待支付订单可以进入PAYING。 */
    boolean markOrderPaying(
            long orderId,
            int expectedVersion,
            LocalDateTime paidBefore,
            LocalDateTime updatedAt);

    /** 固定成功支付只能从PAYING完成到PAID。 */
    boolean markOrderPaid(long orderId, int expectedVersion, LocalDateTime paidAt);

    /** 只有已支付订单可以进入退款处理中。 */
    boolean markOrderRefunding(long orderId, int expectedVersion, LocalDateTime updatedAt);

    /** 只有退款处理中订单可以完成退票并记录完成时间。 */
    boolean markOrderRefunded(long orderId, int expectedVersion, LocalDateTime refundedAt);

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

    /** PAID补偿所需的最小数据库投影，不携带金额、座位、电子票或用户隐私字段。 */
    record PaidTravelReconciliationCandidate(
            long orderId,
            long userId,
            long showId,
            int orderVersion,
            LocalDateTime paidAt) {

        public PaidTravelReconciliationCandidate {
            if (orderId <= 0 || userId <= 0 || showId <= 0 || orderVersion < 0) {
                throw new IllegalArgumentException("PAID出行补偿候选包含非法业务标识或版本");
            }
            Objects.requireNonNull(paidAt, "PAID出行补偿候选的paidAt不能为空");
        }
    }

    /** REFUNDED取消补偿所需的最小投影，不携带金额、座位、票或个人敏感信息。 */
    record RefundedTravelReconciliationCandidate(
            long orderId,
            long userId,
            long showId,
            int orderVersion,
            LocalDateTime refundedAt) {

        public RefundedTravelReconciliationCandidate {
            if (orderId <= 0 || userId <= 0 || showId <= 0 || orderVersion < 0) {
                throw new IllegalArgumentException("REFUNDED出行补偿候选包含非法业务标识或版本");
            }
            Objects.requireNonNull(refundedAt, "REFUNDED出行补偿候选的refundedAt不能为空");
        }
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

    /**
     * 个人订单页面使用的只读投影。
     *
     * <p>影片、影院和开场时间均来自A拥有的movie_show；该投影不包含幂等键，
     * 也不会用于建单、支付、取消或退款的行锁与状态迁移。</p>
     */
    record OrderQuerySnapshot(
            long orderId,
            String orderNo,
            long userId,
            long showId,
            long movieId,
            long cinemaId,
            LocalDateTime showStartTime,
            int ticketCount,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            OrderStatus status,
            LocalDateTime expireTime,
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
