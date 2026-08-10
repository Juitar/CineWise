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

    /** 幂等恢复同时按两类请求键查询；结果仍按 userId 隔离，不能跨账号命中。 */
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

    /** 客户端请求号用于创建订单重试，读取结果时必须保持用户维度。 */
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

    /** 用户订单号查询用于详情读取，不参与支付等需要行锁的交易修改。 */
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

    /** 交易路径在同一数据库事务内加锁读取，随后状态转换仍要校验版本。 */
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

    /** 内部主键读取仅供受控应用服务使用，不可直接作为用户侧详情接口。 */
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
    /** 订单列表统计和分页复用相同用户、状态与创建时间过滤条件。 */
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

    /**
     * refunded_time与id共同组成游标，保证相同毫秒退款不会跨页重复或遗漏。
     * SQL只做候选过滤，跨模块取消前仍由应用层重读订单权威状态。
     */
    /** 列表投影只连接场次必要字段，不加载座位或支付流水等明细。 */
    @Select("""
            SELECT id AS order_id,
                   user_id,
                   show_id,
                   version AS order_version,
                   refunded_time AS refunded_at
              FROM ticket_order
             WHERE status = 'REFUNDED'
               AND refunded_time IS NOT NULL
               AND refunded_time >= #{refundedAtOrAfter}
               AND refunded_time <= #{refundedAtOrBefore}
               AND (refunded_time > #{afterRefundedAt}
                    OR (refunded_time = #{afterRefundedAt} AND id > #{afterOrderId}))
             ORDER BY refunded_time, id
             LIMIT #{limit}
            """)
    List<RefundedTravelReconciliationCandidateRow> findRefundedTravelReconciliationCandidates(
            @Param("refundedAtOrAfter") LocalDateTime refundedAtOrAfter,
            @Param("refundedAtOrBefore") LocalDateTime refundedAtOrBefore,
            @Param("afterRefundedAt") LocalDateTime afterRefundedAt,
            @Param("afterOrderId") long afterOrderId,
            @Param("limit") int limit);

    /** 座位内部 ID 仅供后端再映射为行列号，界面不能直接显示该值。 */
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

    /** 批量座位读取服务于订单列表，调用方先处理空集合以避免 IN ()。 */
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
    /** 操作记录为取消、支付等写请求提供幂等恢复，参数哈希由应用层复核。 */
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

    /** 按本人订单主键读取出行所需最小事实，禁止扩大为交易明细投影。 */
    /** 过期任务只拉取候选订单 ID，真正过期依赖后续条件更新防止误伤已支付订单。 */
    @Select("""
            SELECT orders.id AS order_id,
                   orders.order_no,
                   orders.show_id,
                   shows.movie_id,
                   shows.cinema_id,
                   shows.start_time AS show_start_time
              FROM ticket_order orders
              INNER JOIN movie_show shows ON shows.id = orders.show_id
             WHERE orders.id = #{orderId}
               AND orders.user_id = #{userId}
            """)
    TravelOrderSummarySnapshotRow findTravelOrderSummaryByIdAndUserId(
            @Param("orderId") long orderId,
            @Param("userId") long userId);

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

    /** 创建订单先写主表，座位快照和幂等操作记录由外层同一事务补齐。 */
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

    /** 座位写入保存当时的行列和价格，排片座位调整后仍能准确展示历史订单。 */
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

    /** 幂等操作记录必须和订单结果同事务提交，避免重试读到半完成订单。 */
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

    /** 取消只接受未支付状态和期望版本，支付并发获胜时本次取消返回零行。 */
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

    /** 定时过期要求数据库时间已到期，避免任务扫描和用户支付请求交叉覆盖。 */
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

    /** 支付开始前再次确认未过期，把状态切到 PAYING 后其他操作不能并发支付。 */
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

    /** 支付成功只能从 PAYING 进入 PAID，支付回调重放不会重复推进版本。 */
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
