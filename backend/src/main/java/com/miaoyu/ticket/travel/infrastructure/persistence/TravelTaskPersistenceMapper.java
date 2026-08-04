package com.miaoyu.ticket.travel.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 出行任务最小读写 SQL；唯一键冲突必须交由应用层恢复原任务。 */
@Mapper
public interface TravelTaskPersistenceMapper {

    @Select("""
            SELECT id,
                   task_id AS task_id,
                   user_id AS user_id,
                   order_id AS order_id,
                   show_id AS show_id,
                   cinema_area AS cinema_area,
                   start_at AS start_at,
                   trigger_at AS trigger_at,
                   order_version AS order_version,
                   version,
                   status,
                   closed_at AS closed_at,
                   update_time AS updated_at
              FROM travel_task
             WHERE payment_event_id = #{paymentEventId}
            """)
    TravelTaskRow findByPaymentEventId(@Param("paymentEventId") String paymentEventId);

    @Select("""
            SELECT id, task_id AS task_id, user_id AS user_id, order_id AS order_id,
                   show_id AS show_id, cinema_area AS cinema_area, start_at AS start_at,
                   trigger_at AS trigger_at, order_version AS order_version, version, status,
                   closed_at AS closed_at, update_time AS updated_at
              FROM travel_task
             WHERE invalidation_event_id = #{invalidationEventId}
            """)
    TravelTaskRow findByInvalidationEventId(@Param("invalidationEventId") String invalidationEventId);

    @Select("""
            SELECT id,
                   task_id AS task_id,
                   user_id AS user_id,
                   order_id AS order_id,
                   show_id AS show_id,
                   cinema_area AS cinema_area,
                   start_at AS start_at,
                   trigger_at AS trigger_at,
                   order_version AS order_version,
                   version,
                   status,
                   closed_at AS closed_at,
                   update_time AS updated_at
              FROM travel_task
             WHERE order_id = #{orderId}
            """)
    TravelTaskRow findByOrderId(@Param("orderId") long orderId);

    @Select("""
            SELECT id, task_id AS task_id, user_id AS user_id, order_id AS order_id,
                   show_id AS show_id, cinema_area AS cinema_area, start_at AS start_at,
                   trigger_at AS trigger_at, order_version AS order_version, version, status,
                   closed_at AS closed_at, update_time AS updated_at
              FROM travel_task WHERE id = #{id}
            """)
    TravelTaskRow findById(@Param("id") long id);

    @Select("""
            SELECT id, task_id AS task_id, user_id AS user_id, order_id AS order_id,
                   show_id AS show_id, cinema_area AS cinema_area, start_at AS start_at,
                   trigger_at AS trigger_at, order_version AS order_version, version, status,
                   closed_at AS closed_at, update_time AS updated_at
              FROM travel_task
             WHERE task_id = #{taskId} AND user_id = #{userId}
            """)
    TravelTaskRow findByTaskIdAndUserId(@Param("taskId") String taskId, @Param("userId") long userId);

    @org.apache.ibatis.annotations.Update("""
            UPDATE travel_task
               SET trigger_at = #{triggerAt}, version = version + 1, update_time = #{updatedAt}
             WHERE id = #{id} AND version = #{expectedVersion}
               AND status NOT IN ('COMPLETED', 'CANCELLED', 'FAILED')
            """)
    int updateTriggerAt(
            @Param("id") long id,
            @Param("expectedVersion") long expectedVersion,
            @Param("triggerAt") java.time.LocalDateTime triggerAt,
            @Param("updatedAt") java.time.LocalDateTime updatedAt);

    /**
     * 用订单版本作为退款事件的顺序裁决，不能因旧事件或重复对账取消较新的任务状态。
     *
     * <p>已取消任务仅在收到更高版本退款时更新审计字段；状态与原关闭时间不变，避免被迟到的支付事件重新打开。</p>
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE travel_task
               SET invalidation_event_id = #{invalidationEventId}, order_version = #{orderVersion},
                   version = version + 1, status = 'CANCELLED',
                   closed_at = CASE WHEN status = 'CANCELLED' THEN closed_at ELSE #{closedAt} END,
                   update_time = #{closedAt}
             WHERE id = #{id} AND status NOT IN ('COMPLETED', 'FAILED')
               AND (order_version < #{orderVersion}
                    OR (status <> 'CANCELLED' AND order_version = #{orderVersion}))
            """)
    int cancel(
            @Param("id") long id,
            @Param("orderVersion") long orderVersion,
            @Param("invalidationEventId") String invalidationEventId,
            @Param("closedAt") java.time.LocalDateTime closedAt);

    @Insert("""
            INSERT INTO travel_task (
                id, task_id, payment_event_id, invalidation_event_id,
                user_id, order_id, show_id, cinema_area,
                start_at, trigger_at, order_version, version,
                status, retry_count, closed_at, create_time, update_time
            ) VALUES (
                #{row.id}, #{row.taskId}, #{row.paymentEventId}, NULL,
                #{row.userId}, #{row.orderId}, #{row.showId}, #{row.cinemaArea},
                #{row.startAt}, #{row.triggerAt}, #{row.orderVersion}, 0,
                'PENDING', 0, NULL, #{row.createdAt}, #{row.createdAt}
            )
            """)
    int insert(@Param("row") TravelTaskInsertRow row);

    /** 退款先到时写入取消墓碑，支付补偿只能按订单唯一键读回它。 */
    @Insert("""
            INSERT INTO travel_task (
                id, task_id, payment_event_id, invalidation_event_id,
                user_id, order_id, show_id, cinema_area,
                start_at, trigger_at, order_version, version,
                status, retry_count, closed_at, create_time, update_time
            ) VALUES (
                #{row.id}, #{row.taskId}, NULL, #{row.invalidationEventId},
                #{row.userId}, #{row.orderId}, #{row.showId}, #{row.cinemaArea},
                #{row.startAt}, #{row.triggerAt}, #{row.orderVersion}, 0,
                'CANCELLED', 0, #{row.closedAt}, #{row.closedAt}, #{row.closedAt}
            )
            """)
    int insertCancelled(@Param("row") TravelTaskCancelledInsertRow row);
}
