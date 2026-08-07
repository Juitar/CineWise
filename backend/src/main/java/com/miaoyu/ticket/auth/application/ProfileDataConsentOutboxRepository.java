package com.miaoyu.ticket.auth.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 撤回 outbox 的持久化端口；自动和人工恢复始终操作原事件。 */
public interface ProfileDataConsentOutboxRepository {

  void insert(OutboxEvent event);

  Optional<OutboxEvent> findPendingByEventId(String eventId);

  Optional<OutboxEvent> findExhaustedByEventId(String eventId);

  List<OutboxEvent> findReady(Instant now, int limit);

  boolean markDelivered(String eventId, Instant deliveredAt, Status expectedStatus);

  boolean recordAutomaticFailure(String eventId, int expectedRetryCount, Instant nextAttemptAt, Instant now);

  boolean markExhausted(String eventId, int expectedRetryCount, Instant now);

  record OutboxEvent(
      String eventId,
      long userId,
      long consentVersion,
      long consentRecordVersion,
      Instant occurredAt,
      String traceId,
      Status status,
      int retryCount,
      Instant nextAttemptAt) { }

  enum Status {
    PENDING,
    DELIVERED,
    EXHAUSTED
  }
}
