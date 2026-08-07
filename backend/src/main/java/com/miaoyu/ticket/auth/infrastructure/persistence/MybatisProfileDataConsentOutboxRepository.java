package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.application.ProfileDataConsentOutboxRepository;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** outbox 仓储仅转换 UTC 时间和状态，重试规则留在应用层。 */
@Repository
public class MybatisProfileDataConsentOutboxRepository implements ProfileDataConsentOutboxRepository {
  private final ProfileDataConsentOutboxPersistenceMapper mapper;
  private final BusinessIdGenerator idGenerator;

  public MybatisProfileDataConsentOutboxRepository(
      ProfileDataConsentOutboxPersistenceMapper mapper, BusinessIdGenerator idGenerator) {
    this.mapper = mapper;
    this.idGenerator = idGenerator;
  }

  @Override
  public void insert(OutboxEvent event) {
    int inserted = mapper.insert(new ProfileDataConsentOutboxPersistenceMapper.NewRow(
        idGenerator.nextId(),
        event.eventId(),
        event.userId(),
        event.consentVersion(),
        event.consentRecordVersion(),
        toLocal(event.occurredAt()),
        event.traceId(),
        toLocal(event.nextAttemptAt())));
    if (inserted != 1) {
      throw new IllegalStateException("画像同意撤回 outbox 写入行数异常");
    }
  }

  @Override
  public Optional<OutboxEvent> findPendingByEventId(String eventId) {
    return findByEventIdAndStatus(eventId, Status.PENDING);
  }

  @Override
  public Optional<OutboxEvent> findExhaustedByEventId(String eventId) {
    return findByEventIdAndStatus(eventId, Status.EXHAUSTED);
  }

  @Override
  public List<OutboxEvent> findReady(Instant now, int limit) {
    return mapper.findReady(toLocal(now), limit).stream().map(this::toEvent).toList();
  }

  @Override
  public boolean markDelivered(String eventId, Instant deliveredAt, Status expectedStatus) {
    return mapper.markDelivered(eventId, expectedStatus.name(), toLocal(deliveredAt)) == 1;
  }

  @Override
  public boolean recordAutomaticFailure(
      String eventId, int expectedRetryCount, Instant nextAttemptAt, Instant now) {
    return mapper.recordAutomaticFailure(
        eventId, expectedRetryCount, toLocal(nextAttemptAt), toLocal(now)) == 1;
  }

  @Override
  public boolean markExhausted(String eventId, int expectedRetryCount, Instant now) {
    return mapper.markExhausted(eventId, expectedRetryCount, toLocal(now)) == 1;
  }

  private Optional<OutboxEvent> findByEventIdAndStatus(String eventId, Status status) {
    return Optional.ofNullable(mapper.findByEventIdAndStatus(eventId, status.name())).map(this::toEvent);
  }

  private OutboxEvent toEvent(ProfileDataConsentOutboxPersistenceMapper.Row row) {
    return new OutboxEvent(
        row.eventId(),
        row.userId(),
        row.consentVersion(),
        row.consentRecordVersion(),
        toInstant(row.occurredAt()),
        row.traceId(),
        Status.valueOf(row.status()),
        row.retryCount(),
        toInstant(row.nextAttemptAt()));
  }

  private LocalDateTime toLocal(Instant instant) {
    return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private Instant toInstant(LocalDateTime time) {
    return time == null ? null : time.toInstant(ZoneOffset.UTC);
  }
}
