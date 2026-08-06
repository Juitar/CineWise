package com.miaoyu.ticket.profile.application;

import java.time.LocalDateTime;
import java.util.Optional;

/** profile_write_request 的应用层端口，用于进程重启后的幂等响应恢复。 */
public interface ProfileWriteRequestRepository {
  Optional<Snapshot> findByUserIdAndOperationAndIdempotencyKey(
      long userId, String operation, String idempotencyKey);

  void insert(NewRequest request);

  record NewRequest(
      long id,
      long userId,
      String operation,
      String idempotencyKey,
      String requestHash,
      int httpStatus,
      String responseJson,
      LocalDateTime completedAt,
      LocalDateTime expiresAt) { }

  record Snapshot(
      long userId,
      String operation,
      String idempotencyKey,
      String requestHash,
      int httpStatus,
      String responseJson,
      LocalDateTime expiresAt) { }
}
