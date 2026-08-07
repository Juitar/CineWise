package com.miaoyu.ticket.auth.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 撤回 outbox SQL 以 eventId、状态和 retryCount 做条件更新，避免并发调度相互覆盖。 */
@Mapper
public interface ProfileDataConsentOutboxPersistenceMapper {

  @Insert("""
      INSERT INTO sys_profile_data_consent_outbox (
          id, event_id, user_id, consent_version, consent_record_version,
          occurred_at, trace_id, status, retry_count, next_attempt_at,
          delivered_at, create_time, update_time
      ) VALUES (
          #{id}, #{eventId}, #{userId}, #{consentVersion}, #{consentRecordVersion},
          #{occurredAt}, #{traceId}, 'PENDING', 0, #{nextAttemptAt},
          NULL, #{occurredAt}, #{occurredAt}
      )
      """)
  int insert(NewRow row);

  @Select("""
      SELECT event_id, user_id, consent_version, consent_record_version,
             occurred_at, trace_id, status, retry_count, next_attempt_at
        FROM sys_profile_data_consent_outbox
       WHERE event_id = #{eventId}
         AND status = #{status}
      """)
  Row findByEventIdAndStatus(@Param("eventId") String eventId, @Param("status") String status);

  @Select("""
      SELECT event_id, user_id, consent_version, consent_record_version,
             occurred_at, trace_id, status, retry_count, next_attempt_at
        FROM sys_profile_data_consent_outbox
       WHERE status = 'PENDING'
         AND next_attempt_at <= #{now}
       ORDER BY next_attempt_at, event_id
       LIMIT #{limit}
      """)
  List<Row> findReady(@Param("now") LocalDateTime now, @Param("limit") int limit);

  @Update("""
      UPDATE sys_profile_data_consent_outbox
         SET status = 'DELIVERED', delivered_at = #{now}, next_attempt_at = NULL, update_time = #{now}
       WHERE event_id = #{eventId}
         AND status = #{expectedStatus}
      """)
  int markDelivered(
      @Param("eventId") String eventId,
      @Param("expectedStatus") String expectedStatus,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE sys_profile_data_consent_outbox
         SET retry_count = retry_count + 1,
             next_attempt_at = #{nextAttemptAt},
             update_time = #{now}
       WHERE event_id = #{eventId}
         AND status = 'PENDING'
         AND retry_count = #{expectedRetryCount}
         AND retry_count < 10
      """)
  int recordAutomaticFailure(
      @Param("eventId") String eventId,
      @Param("expectedRetryCount") int expectedRetryCount,
      @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE sys_profile_data_consent_outbox
         SET status = 'EXHAUSTED',
             retry_count = retry_count + 1,
             next_attempt_at = NULL,
             update_time = #{now}
       WHERE event_id = #{eventId}
         AND status = 'PENDING'
         AND retry_count = #{expectedRetryCount}
         AND retry_count = 9
      """)
  int markExhausted(
      @Param("eventId") String eventId,
      @Param("expectedRetryCount") int expectedRetryCount,
      @Param("now") LocalDateTime now);

  record NewRow(
      long id,
      String eventId,
      long userId,
      long consentVersion,
      long consentRecordVersion,
      LocalDateTime occurredAt,
      String traceId,
      LocalDateTime nextAttemptAt) { }

  record Row(
      String eventId,
      long userId,
      long consentVersion,
      long consentRecordVersion,
      LocalDateTime occurredAt,
      String traceId,
      String status,
      int retryCount,
      LocalDateTime nextAttemptAt) { }
}
