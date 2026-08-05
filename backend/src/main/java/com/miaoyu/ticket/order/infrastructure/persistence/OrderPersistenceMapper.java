package com.miaoyu.ticket.order.infrastructure.persistence;

import com.miaoyu.ticket.order.application.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 订单幂等恢复、主表和座位快照的显式SQL。 */
@Mapper
public interface OrderPersistenceMapper {

    @Select("""
            SELECT id AS order_id,
                   order_no,
                   user_id,
                   show_id,
                   ticket_count,
                   unit_price,
                   total_amount,
                   status,
                   expire_time,
                   client_request_id,
                   idempotency_key,
                   version,
                   update_time AS updated_at
              FROM ticket_order
             WHERE user_id = #{userId}
               AND (client_request_id = #{clientRequestId}
                    OR idempotency_key = #{idempotencyKey})
             ORDER BY id
            """)
    List<OrderSnapshotRow> findByRequestKeys(
            @Param("userId") long userId,
            @Param("clientRequestId") String clientRequestId,
            @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT id AS order_id,
                   order_no,
                   user_id,
                   show_id,
                   ticket_count,
                   unit_price,
                   total_amount,
                   status,
                   expire_time,
                   client_request_id,
                   idempotency_key,
                   version,
                   update_time AS updated_at
              FROM ticket_order
             WHERE user_id = #{userId}
               AND client_request_id = #{clientRequestId}
            """)
    OrderSnapshotRow findByClientRequestId(
            @Param("userId") long userId,
            @Param("clientRequestId") String clientRequestId);

    @Select("""
            SELECT id AS order_id,
                   order_no,
                   user_id,
                   show_id,
                   ticket_count,
                   unit_price,
                   total_amount,
                   status,
                   expire_time,
                   client_request_id,
                   idempotency_key,
                   version,
                   update_time AS updated_at
              FROM ticket_order
             WHERE user_id = #{userId}
               AND order_no = #{orderNo}
            """)
    OrderSnapshotRow findByOrderNo(
            @Param("userId") long userId,
            @Param("orderNo") String orderNo);

    @Select("""
            SELECT id AS order_id,
                   order_no,
                   user_id,
                   show_id,
                   ticket_count,
                   unit_price,
                   total_amount,
                   status,
                   expire_time,
                   client_request_id,
                   idempotency_key,
                   version,
                   update_time AS updated_at
              FROM ticket_order
             WHERE user_id = #{userId}
               AND order_no = #{orderNo}
             FOR UPDATE
            """)
    OrderSnapshotRow findByOrderNoForUpdate(
            @Param("userId") long userId,
            @Param("orderNo") String orderNo);

    @Select("""
            SELECT id AS order_id,
                   order_no,
                   user_id,
                   show_id,
                   ticket_count,
                   unit_price,
                   total_amount,
                   status,
                   expire_time,
                   client_request_id,
                   idempotency_key,
                   version,
                   update_time AS updated_at
              FROM ticket_order
             WHERE id = #{orderId}
            """)
    OrderSnapshotRow findById(@Param("orderId") long orderId);

    /**
     * paid_time与id共同组成游标，避免相同毫秒支付的订单在分页边界重复或遗漏。
     * status在数据库查询层先过滤，应用层仍会在调用D前重读状态和版本。
     */
    @Select("""
            SELECT id AS order_id,
                   user_id,
                   show_id,
                   version AS order_version,
                   paid_time AS paid_at
              FROM ticket_order
             WHERE status = 'PAID'
               AND paid_time IS NOT NULL
               AND paid_time >= #{paidAtOrAfter}
               AND paid_time <= #{paidAtOrBefore}
               AND (paid_time > #{afterPaidAt}
                    OR (paid_time = #{afterPaidAt} AND id > #{afterOrderId}))
             ORDER BY paid_time, id
             LIMIT #{limit}
            """)
    List<PaidTravelReconciliationCandidateRow> findPaidTravelReconciliationCandidates(
            @Param("paidAtOrAfter") LocalDateTime paidAtOrAfter,
            @Param("paidAtOrBefore") LocalDateTime paidAtOrBefore,
            @Param("afterPaidAt") LocalDateTime afterPaidAt,
            @Param("afterOrderId") long afterOrderId,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM ticket_order orders
              INNER JOIN movie_show shows ON shows.id = orders.show_id
             WHERE orders.user_id = #{criteria.userId}
            <if test="criteria.orderNo != null">
               AND orders.order_no = #{criteria.orderNo}
            </if>
            <if test="criteria.status != null">
               AND orders.status = #{criteria.status}
            </if>
            <if test="criteria.createdAtOrAfter != null">
               AND orders.create_time &gt;= #{criteria.createdAtOrAfter}
            </if>
            <if test="criteria.createdBefore != null">
               AND orders.create_time &lt; #{criteria.createdBefore}
            </if>
            </script>
            """)
    long countOrders(@Param("criteria") OrderRepository.OrderListCriteria criteria);

    @Select("""
            <script>
            SELECT orders.id AS order_id,
                   orders.order_no,
                   orders.user_id,
                   orders.show_id,
                   shows.movie_id,
                   shows.cinema_id,
                   shows.start_time AS show_start_time,
                   orders.ticket_count,
                   orders.unit_price,
                   orders.total_amount,
                   orders.status,
                   orders.expire_time,
                   orders.version,
                   orders.update_time AS updated_at
              FROM ticket_order orders
              INNER JOIN movie_show shows ON shows.id = orders.show_id
             WHERE orders.user_id = #{criteria.userId}
            <if test="criteria.orderNo != null">
               AND orders.order_no = #{criteria.orderNo}
            </if>
            <if test="criteria.status != null">
               AND orders.status = #{criteria.status}
            </if>
            <if test="criteria.createdAtOrAfter != null">
               AND orders.create_time &gt;= #{criteria.createdAtOrAfter}
            </if>
            <if test="criteria.createdBefore != null">
               AND orders.create_time &lt; #{criteria.createdBefore}
            </if>
             ORDER BY orders.create_time DESC, orders.id DESC
             LIMIT #{criteria.limit} OFFSET #{criteria.offset}
            </script>
            """)
    List<OrderQuerySnapshotRow> findOrderQueryPage(
            @Param("criteria") OrderRepository.OrderListCriteria criteria);

    /** 详情只读投影连接场次事实；交易用的findByOrderNo和FOR UPDATE查询保持独立。 */
    @Select("""
            SELECT orders.id AS order_id,
                   orders.order_no,
                   orders.user_id,
                   orders.show_id,
                   shows.movie_id,
                   shows.cinema_id,
                   shows.start_time AS show_start_time,
                   orders.ticket_count,
                   orders.unit_price,
                   orders.total_amount,
                   orders.status,
                   orders.expire_time,
                   orders.version,
                   orders.update_time AS updated_at
              FROM ticket_order orders
              INNER JOIN movie_show shows ON shows.id = orders.show_id
             WHERE orders.user_id = #{userId}
               AND orders.order_no = #{orderNo}
            """)
    OrderQuerySnapshotRow findOrderQueryByOrderNo(
            @Param("userId") long userId,
            @Param("orderNo") String orderNo);

    @Select("""
            SELECT show_seat_id
              FROM ticket_order_seat
             WHERE order_id = #{orderId}
             ORDER BY show_seat_id
            """)
    List<Long> findSeatIds(@Param("orderId") long orderId);

    @Select("""
            <script>
            SELECT order_id,
                   show_seat_id AS seat_id
              FROM ticket_order_seat
             WHERE order_id IN
               <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">
                   #{orderId}
               </foreach>
             ORDER BY order_id, show_seat_id
            </script>
            """)
    List<OrderSeatReferenceRow> findSeatIdsByOrderIds(@Param("orderIds") List<Long> orderIds);

    @Select("""
            SELECT id,
                   user_id,
                   action,
                   idempotency_key,
                   order_id,
                   order_no_snapshot,
                   parameter_hash,
                   result_status,
                   result_version,
                   create_time AS created_at
              FROM ticket_order_operation
             WHERE user_id = #{userId}
               AND action = #{action}
               AND idempotency_key = #{idempotencyKey}
            """)
    OrderOperationRow findOperation(
            @Param("userId") long userId,
            @Param("action") String action,
            @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT id
             FROM ticket_order
             WHERE status = 'PENDING_PAYMENT'
               AND expire_time <= #{expiresAtOrBefore}
             ORDER BY expire_time, id
             LIMIT #{limit}
            """)
    List<Long> findExpiredCandidateIds(
            @Param("expiresAtOrBefore") LocalDateTime expiresAtOrBefore,
            @Param("limit") int limit);

    @Insert("""
            INSERT INTO ticket_order (
                id, order_no, user_id, show_id, ticket_count,
                unit_price, total_amount, status, expire_time,
                client_request_id, idempotency_key, version,
                paid_time, cancelled_time, refunded_time,
                create_time, update_time
            ) VALUES (
                #{row.orderId}, #{row.orderNo}, #{row.userId}, #{row.showId}, #{row.ticketCount},
                #{row.unitPrice}, #{row.totalAmount}, #{row.status}, #{row.expireTime},
                #{row.clientRequestId}, #{row.idempotencyKey}, 0,
                NULL, NULL, NULL,
                #{row.createdAt}, #{row.createdAt}
            )
            """)
    int insertOrder(@Param("row") OrderInsertRow row);

    @Insert("""
            INSERT INTO ticket_order_seat (
                id, order_id, show_seat_id, row_no_snapshot,
                seat_no_snapshot, unit_price, create_time, update_time
            ) VALUES (
                #{row.id}, #{row.orderId}, #{row.showSeatId}, #{row.rowNoSnapshot},
                #{row.seatNoSnapshot}, #{row.unitPrice}, #{row.createdAt}, #{row.createdAt}
            )
            """)
    int insertOrderSeat(@Param("row") OrderSeatInsertRow row);

    @Insert("""
            INSERT INTO ticket_order_operation (
                id, user_id, action, idempotency_key, order_id,
                order_no_snapshot, parameter_hash, result_status,
                result_version, create_time, update_time
            ) VALUES (
                #{row.id}, #{row.userId}, #{row.action}, #{row.idempotencyKey}, #{row.orderId},
                #{row.orderNoSnapshot}, #{row.parameterHash}, #{row.resultStatus},
                #{row.resultVersion}, #{row.createdAt}, #{row.createdAt}
            )
            """)
    int insertOperation(@Param("row") OrderOperationInsertRow row);

    @Update("""
            UPDATE ticket_order
               SET status = 'CANCELLED',
                   cancelled_time = #{cancelledAt},
                   version = version + 1,
                   update_time = #{cancelledAt}
             WHERE id = #{orderId}
               AND status = 'PENDING_PAYMENT'
               AND version = #{expectedVersion}
            """)
    int cancelOrder(
            @Param("orderId") long orderId,
            @Param("expectedVersion") int expectedVersion,
            @Param("cancelledAt") LocalDateTime cancelledAt);

    @Update("""
            UPDATE ticket_order
               SET status = 'EXPIRED',
                   version = version + 1,
                   update_time = #{updatedAt}
             WHERE id = #{orderId}
               AND status = 'PENDING_PAYMENT'
               AND expire_time <= #{expiresAtOrBefore}
               AND version = #{expectedVersion}
            """)
    int expireOrder(
            @Param("orderId") long orderId,
            @Param("expectedVersion") int expectedVersion,
            @Param("expiresAtOrBefore") LocalDateTime expiresAtOrBefore,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE ticket_order
               SET status = 'PAYING',
                   version = version + 1,
                   update_time = #{updatedAt}
             WHERE id = #{orderId}
               AND status = 'PENDING_PAYMENT'
               AND expire_time > #{paidBefore}
               AND version = #{expectedVersion}
            """)
    int markOrderPaying(
            @Param("orderId") long orderId,
            @Param("expectedVersion") int expectedVersion,
            @Param("paidBefore") LocalDateTime paidBefore,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE ticket_order
               SET status = 'PAID',
                   paid_time = #{paidAt},
                   version = version + 1,
                   update_time = #{paidAt}
             WHERE id = #{orderId}
               AND status = 'PAYING'
               AND version = #{expectedVersion}
            """)
    int markOrderPaid(
            @Param("orderId") long orderId,
            @Param("expectedVersion") int expectedVersion,
            @Param("paidAt") LocalDateTime paidAt);

    /** 订单状态和版本共同防止重复退票或其他流程覆盖已支付终态。 */
    @Update("""
            UPDATE ticket_order
               SET status = 'REFUNDING',
                   version = version + 1,
                   update_time = #{updatedAt}
             WHERE id = #{orderId}
               AND status = 'PAID'
               AND version = #{expectedVersion}
            """)
    int markOrderRefunding(
            @Param("orderId") long orderId,
            @Param("expectedVersion") int expectedVersion,
            @Param("updatedAt") LocalDateTime updatedAt);

    /** 只有同一事务已经进入REFUNDING的订单才能形成退款终态。 */
    @Update("""
            UPDATE ticket_order
               SET status = 'REFUNDED',
                   refunded_time = #{refundedAt},
                   version = version + 1,
                   update_time = #{refundedAt}
             WHERE id = #{orderId}
               AND status = 'REFUNDING'
               AND version = #{expectedVersion}
            """)
    int markOrderRefunded(
            @Param("orderId") long orderId,
            @Param("expectedVersion") int expectedVersion,
            @Param("refundedAt") LocalDateTime refundedAt);
}
