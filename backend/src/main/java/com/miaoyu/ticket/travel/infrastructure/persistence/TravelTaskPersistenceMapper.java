package com.miaoyu.ticket.travel.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 出行任务最小读写 SQL；唯一键冲突必须交由应用层恢复原任务。 */
@Mapper
public interface TravelTaskPersistenceMapper {

    @Select("""
            SELECT id, task_id AS task_id, user_id AS user_id, order_id AS order_id,
                   show_id AS show_id, cinema_id AS cinema_id, cinema_area AS cinema_area,
                   start_at AS start_at, trigger_at AS trigger_at, order_version AS order_version,
                   version, status, closed_at AS closed_at, update_time AS updated_at
              FROM travel_task WHERE payment_event_id = #{paymentEventId}
            """)
    TravelTaskRow findByPaymentEventId(@Param("paymentEventId") String paymentEventId);

    @Select("""
            SELECT id, task_id AS task_id, user_id AS user_id, order_id AS order_id,
                   show_id AS show_id, cinema_id AS cinema_id, cinema_area AS cinema_area,
                   start_at AS start_at, trigger_at AS trigger_at, order_version AS order_version,
                   version, status, closed_at AS closed_at, update_time AS updated_at
              FROM travel_task WHERE invalidation_event_id = #{invalidationEventId}
            """)
    TravelTaskRow findByInvalidationEventId(@Param("invalidationEventId") String invalidationEventId);

    @Select("""
            SELECT id, task_id AS task_id, user_id AS user_id, order_id AS order_id,
                   show_id AS show_id, cinema_id AS cinema_id, cinema_area AS cinema_area,
                   start_at AS start_at, trigger_at AS trigger_at, order_version AS order_version,
                   version, status, closed_at AS closed_at, update_time AS updated_at
              FROM travel_task WHERE order_id = #{orderId}
            """)
    TravelTaskRow findByOrderId(@Param("orderId") long orderId);

    @Select("""
            SELECT id, task_id AS task_id, user_id AS user_id, order_id AS order_id,
                   show_id AS show_id, cinema_id AS cinema_id, cinema_area AS cinema_area,
                   start_at AS start_at, trigger_at AS trigger_at, order_version AS order_version,
                   version, status, closed_at AS closed_at, update_time AS updated_at
              FROM travel_task WHERE id = #{id}
            """)
    TravelTaskRow findById(@Param("id") long id);

    @Select("""
            SELECT id, task_id AS task_id, user_id AS user_id, order_id AS order_id,
                   show_id AS show_id, cinema_id AS cinema_id, cinema_area AS cinema_area,
                   start_at AS start_at, trigger_at AS trigger_at, order_version AS order_version,
                   version, status, closed_at AS closed_at, update_time AS updated_at
              FROM travel_task WHERE task_id = #{taskId} AND user_id = #{userId}
            """)
    TravelTaskRow findByTaskIdAndUserId(@Param("taskId") String taskId, @Param("userId") long userId);

    /** 批量读取只作为候选；后续生成仍由版本条件更新决定唯一赢家。 */
    @Select("""
            SELECT id, task_id AS task_id, user_id AS user_id, order_id AS order_id,
                   show_id AS show_id, cinema_id AS cinema_id, cinema_area AS cinema_area,
                   start_at AS start_at, trigger_at AS trigger_at, order_version AS order_version,
                   version, status, closed_at AS closed_at, update_time AS updated_at
              FROM travel_task
             WHERE status = 'PENDING' AND trigger_at <= #{now} AND start_at > #{now}
             ORDER BY trigger_at ASC, id ASC LIMIT #{limit}
            """)
    List<TravelTaskRow> listDueForAdvice(@Param("now") java.time.LocalDateTime now, @Param("limit") int limit);

    @org.apache.ibatis.annotations.Update("""
            UPDATE travel_task SET status = 'COMPLETED', closed_at = #{closedAt}, update_time = #{closedAt}
             WHERE id = #{id} AND status IN ('PENDING', 'READY', 'NOTIFIED') AND start_at <= #{elapsedBefore}
            """)
    int completeIfElapsed(@Param("id") long id, @Param("elapsedBefore") java.time.LocalDateTime elapsedBefore,
                          @Param("closedAt") java.time.LocalDateTime closedAt);

    @Select("""
            SELECT id FROM travel_task
             WHERE status IN ('PENDING', 'READY', 'NOTIFIED') AND start_at <= #{completedAt}
             ORDER BY start_at ASC, id ASC LIMIT #{limit}
            """)
    List<Long> listElapsedTaskIds(@Param("completedAt") java.time.LocalDateTime completedAt, @Param("limit") int limit);

    @org.apache.ibatis.annotations.Update("""
            UPDATE travel_task
               SET trigger_at = #{triggerAt}, version = version + 1, update_time = #{updatedAt}
             WHERE id = #{id} AND version = #{expectedVersion}
               AND status NOT IN ('COMPLETED', 'CANCELLED', 'FAILED')
            """)
    int updateTriggerAt(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                        @Param("triggerAt") java.time.LocalDateTime triggerAt,
                        @Param("updatedAt") java.time.LocalDateTime updatedAt);

    /** 退款只更新状态和版本，不能用退款事件的影院 ID 覆盖已创建任务的原始值。 */
    @org.apache.ibatis.annotations.Update("""
            UPDATE travel_task
               SET invalidation_event_id = #{invalidationEventId}, order_version = #{orderVersion},
                   version = version + 1, closed_at = COALESCE(closed_at, #{closedAt}),
                   status = 'CANCELLED',
                   update_time = #{closedAt}
             WHERE id = #{id} AND status NOT IN ('COMPLETED', 'FAILED')
               AND (order_version < #{orderVersion}
                    OR (status <> 'CANCELLED' AND order_version = #{orderVersion}))
            """)
    int cancel(@Param("id") long id, @Param("orderVersion") long orderVersion,
               @Param("invalidationEventId") String invalidationEventId,
               @Param("closedAt") java.time.LocalDateTime closedAt);

    @Insert("""
            INSERT INTO travel_task (
                id, task_id, payment_event_id, invalidation_event_id,
                user_id, order_id, show_id, cinema_id, cinema_area,
                start_at, trigger_at, order_version, version,
                status, retry_count, closed_at, create_time, update_time
            ) VALUES (
                #{row.id}, #{row.taskId}, #{row.paymentEventId}, NULL,
                #{row.userId}, #{row.orderId}, #{row.showId}, #{row.cinemaId}, #{row.cinemaArea},
                #{row.startAt}, #{row.triggerAt}, #{row.orderVersion}, 0,
                'PENDING', 0, NULL, #{row.createdAt}, #{row.createdAt}
            )
            """)
    int insert(@Param("row") TravelTaskInsertRow row);

    /** 退款先到时允许无效影院 ID 写为 NULL，保留终态墓碑以阻止迟到支付重新开启任务。 */
    @Insert("""
            INSERT INTO travel_task (
                id, task_id, payment_event_id, invalidation_event_id,
                user_id, order_id, show_id, cinema_id, cinema_area,
                start_at, trigger_at, order_version, version,
                status, retry_count, closed_at, create_time, update_time
            ) VALUES (
                #{row.id}, #{row.taskId}, NULL, #{row.invalidationEventId},
                #{row.userId}, #{row.orderId}, #{row.showId}, #{row.cinemaId}, #{row.cinemaArea},
                #{row.startAt}, #{row.triggerAt}, #{row.orderVersion}, 0,
                'CANCELLED', 0, #{row.closedAt}, #{row.closedAt}, #{row.closedAt}
            )
            """)
    int insertCancelled(@Param("row") TravelTaskCancelledInsertRow row);
}
