package com.miaoyu.ticket.profile.infrastructure.persistence;

import com.miaoyu.ticket.profile.application.ProfileWriteRequestRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 幂等记录只按三元唯一键读取，业务服务负责比较摘要并决定重放或拒绝。 */
@Mapper
public interface ProfileWriteRequestPersistenceMapper {
  @Select(
      """
      SELECT user_id AS user_id, operation, idempotency_key AS idempotency_key,
             request_hash AS request_hash, http_status AS http_status,
             response_json AS response_json, expires_at AS expires_at
        FROM profile_write_request
       WHERE user_id = #{userId} AND operation = #{operation}
         AND idempotency_key = #{idempotencyKey}
      """)
  ProfileWriteRequestRow findByKey(
      @Param("userId") long userId,
      @Param("operation") String operation,
      @Param("idempotencyKey") String idempotencyKey);

  @Insert(
      """
INSERT INTO profile_write_request (id, user_id, operation, idempotency_key, request_hash, status,
    http_status, response_json, completed_at, expires_at, create_time, update_time)
VALUES (#{request.id}, #{request.userId}, #{request.operation}, #{request.idempotencyKey},
    #{request.requestHash}, 'COMPLETED', #{request.httpStatus}, #{request.responseJson},
    #{request.completedAt}, #{request.expiresAt}, #{request.completedAt}, #{request.completedAt})
""")
  int insert(@Param("request") ProfileWriteRequestRepository.NewRequest request);

  @org.apache.ibatis.annotations.Delete(
      "DELETE FROM profile_write_request WHERE expires_at <= #{before} LIMIT #{limit}")
  int cleanupExpired(
      @Param("before") java.time.LocalDateTime before, @Param("limit") int limit);
}
