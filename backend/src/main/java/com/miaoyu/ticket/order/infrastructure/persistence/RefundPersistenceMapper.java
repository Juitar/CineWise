package com.miaoyu.ticket.order.infrastructure.persistence;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 退款幂等查询、状态迁移和JSON影响快照的显式SQL。
 *
 * <ul>
 *   <li>查询只列出退款恢复所需字段，不使用SELECT *；</li>
 *   <li>INSERT固定创建REQUESTED，调用方不能传入任意状态；</li>
 *   <li>UPDATE同时检查前置状态和version；</li>
 *   <li>所有参数使用MyBatis预编译绑定，不拼接用户输入；</li>
 *   <li>Mapper只返回影响行数，事务服务决定冲突和回滚语义。</li>
 * </ul>
 */
@Mapper
public interface RefundPersistenceMapper {

    /** 每订单唯一查询用于重复退票和结果恢复。 */
    @Select("""
            SELECT id AS refund_id,
                   refund_no,
                   order_id,
                   user_id,
                   idempotency_key,
                   action_id,
                   reason,
                   impact_snapshot,
                   status,
                   request_time,
                   processed_time,
                   version,
                   update_time AS updated_at
              FROM refund_request
             WHERE order_id = #{orderId}
            """)
    RefundSnapshotRow findByOrderId(@Param("orderId") long orderId);

    /** 用户条件防止幂等查询成为跨用户退款记录探测入口。 */
    @Select("""
            SELECT id AS refund_id,
                   refund_no,
                   order_id,
                   user_id,
                   idempotency_key,
                   action_id,
                   reason,
                   impact_snapshot,
                   status,
                   request_time,
                   processed_time,
                   version,
                   update_time AS updated_at
              FROM refund_request
             WHERE user_id = #{userId}
               AND idempotency_key = #{idempotencyKey}
            """)
    RefundSnapshotRow findByUserAndIdempotencyKey(
            @Param("userId") long userId,
            @Param("idempotencyKey") String idempotencyKey);

    /** 先建立REQUESTED记录，使后续状态变化始终有唯一退款聚合承载。 */
    @Insert("""
            INSERT INTO refund_request (
                id, refund_no, order_id, user_id,
                idempotency_key, action_id, reason, impact_snapshot,
                status, request_time, processed_time, version,
                create_time, update_time
            ) VALUES (
                #{row.refundId}, #{row.refundNo}, #{row.orderId}, #{row.userId},
                #{row.idempotencyKey}, #{row.actionId}, #{row.reason}, #{row.impactSnapshot},
                'REQUESTED', #{row.requestedAt}, NULL, 0,
                #{row.requestedAt}, #{row.requestedAt}
            )
            """)
    int insertRequestedRefund(@Param("row") RefundInsertRow row);

    /** REQUESTED和版本条件共同禁止跳过状态机或重复处理。 */
    @Update("""
            UPDATE refund_request
               SET status = 'PROCESSING',
                   version = version + 1,
                   update_time = #{updatedAt}
             WHERE id = #{refundId}
               AND status = 'REQUESTED'
               AND version = #{expectedVersion}
            """)
    int markProcessing(
            @Param("refundId") long refundId,
            @Param("expectedVersion") int expectedVersion,
            @Param("updatedAt") LocalDateTime updatedAt);

    /** SUCCESS只允许从PROCESSING形成，并记录唯一处理完成时间。 */
    @Update("""
            UPDATE refund_request
               SET status = 'SUCCESS',
                   processed_time = #{processedAt},
                   version = version + 1,
                   update_time = #{processedAt}
             WHERE id = #{refundId}
               AND status = 'PROCESSING'
               AND version = #{expectedVersion}
            """)
    int markSuccess(
            @Param("refundId") long refundId,
            @Param("expectedVersion") int expectedVersion,
            @Param("processedAt") LocalDateTime processedAt);
}
