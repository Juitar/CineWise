package com.miaoyu.ticket.profile.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import com.miaoyu.ticket.profile.application.ProfileBehaviorEventRepository;

/** 行为查询只读取归一化所需字段，原始 payload 不进入 D 的应用层。 */
@Mapper
public interface ProfileBehaviorEventPersistenceMapper {
  @Select(
      """
      SELECT event_id AS event_id, user_id AS user_id, event_type AS event_type,
             target_type AS target_type, target_id AS target_id, occurred_at AS occurred_at,
             COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.changed')) = 'true', FALSE) AS changed
        FROM user_behavior_event WHERE event_id = #{eventId}
      """)
  ProfileBehaviorEventRow findByEventId(@Param("eventId") String eventId);

  @Select(
      """
      SELECT EXISTS(SELECT 1 FROM user_behavior_event
       WHERE user_id = #{userId} AND event_type = #{eventType}
         AND target_type = #{targetType} AND target_id = #{targetId} AND occurred_at >= #{since})
      """)
    boolean existsInWindow(
      @Param("userId") long userId,
      @Param("eventType") String eventType,
      @Param("targetType") String targetType,
      @Param("targetId") String targetId,
            @Param("since") java.time.LocalDateTime since);

    @Insert("""
            INSERT INTO user_behavior_event (id, event_id, user_id, event_type, target_type, target_id,
                order_id, order_version, session_id, payload_json, occurred_at, create_time)
            VALUES (#{event.id}, #{event.eventId}, #{event.userId}, #{event.eventType}, #{event.targetType},
                #{event.targetId}, #{event.orderId}, #{event.orderVersion}, NULL,
                CASE WHEN #{event.changed} THEN '{"changed":true}' ELSE '{"changed":false}' END,
                #{event.occurredAt}, #{event.createdAt})
            """)
  int insert(@Param("event") ProfileBehaviorEventRepository.NewEvent event);

  @org.apache.ibatis.annotations.Delete(
      "DELETE FROM user_behavior_event WHERE occurred_at <= #{before} LIMIT #{limit}")
  int cleanupBefore(
      @Param("before") java.time.LocalDateTime before, @Param("limit") int limit);
}
