package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.profile.application.ProfileDataConsentWithdrawnEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 持久化 outbox 的投递与恢复规则，投递失败只修改原事件的重试状态。 */
@Service
public class ProfileDataConsentOutboxDeliveryService {
  private static final int MAX_AUTOMATIC_FAILURES = 10;
  private static final int SCAN_LIMIT = 100;
  private static final List<Duration> RETRY_DELAYS = List.of(
      Duration.ofMinutes(1),
      Duration.ofMinutes(5),
      Duration.ofMinutes(15),
      Duration.ofMinutes(60),
      Duration.ofMinutes(360));
  private static final Duration LATER_RETRY_DELAY = Duration.ofHours(6);

  private final ProfileDataConsentOutboxRepository outboxRepository;
  private final ProfileDataConsentWithdrawalPublisher publisher;
  private final Clock clock;

  public ProfileDataConsentOutboxDeliveryService(
      ProfileDataConsentOutboxRepository outboxRepository,
      ProfileDataConsentWithdrawalPublisher publisher,
      Clock clock) {
    this.outboxRepository = outboxRepository;
    this.publisher = publisher;
    this.clock = clock;
  }

  /** 事务提交后的首次投递；失败计入自动重试次数并安排下一次时间。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean deliverPending(String eventId) {
    return outboxRepository.findPendingByEventId(eventId).map(this::deliverAutomatic).orElse(false);
  }

  /** 定时任务每次只处理有限条记录，单条失败不会阻止后续事件。 */
  public List<String> listReadyEventIds() {
    return outboxRepository.findReady(clock.instant(), SCAN_LIMIT).stream()
        .map(ProfileDataConsentOutboxRepository.OutboxEvent::eventId)
        .toList();
  }

  /** 人工恢复仅允许 EXHAUSTED 事件，失败时状态和 retryCount 保持不变。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean recoverExhausted(String eventId) {
    ProfileDataConsentOutboxRepository.OutboxEvent event =
        outboxRepository.findExhaustedByEventId(eventId).orElse(null);
    if (event == null) {
      return false;
    }
    try {
      publisher.publish(toEvent(event));
      return outboxRepository.markDelivered(
          event.eventId(), clock.instant(), ProfileDataConsentOutboxRepository.Status.EXHAUSTED);
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private boolean deliverAutomatic(ProfileDataConsentOutboxRepository.OutboxEvent event) {
    Instant now = clock.instant();
    try {
      publisher.publish(toEvent(event));
      return outboxRepository.markDelivered(
          event.eventId(), now, ProfileDataConsentOutboxRepository.Status.PENDING);
    } catch (RuntimeException exception) {
      int nextRetryCount = event.retryCount() + 1;
      if (nextRetryCount >= MAX_AUTOMATIC_FAILURES) {
        outboxRepository.markExhausted(event.eventId(), event.retryCount(), now);
      } else {
        outboxRepository.recordAutomaticFailure(
            event.eventId(), event.retryCount(), now.plus(delayFor(nextRetryCount)), now);
      }
      return false;
    }
  }

  private Duration delayFor(int failureCount) {
    int index = failureCount - 1;
    return index < RETRY_DELAYS.size() ? RETRY_DELAYS.get(index) : LATER_RETRY_DELAY;
  }

  private ProfileDataConsentWithdrawnEvent toEvent(
      ProfileDataConsentOutboxRepository.OutboxEvent event) {
    return new ProfileDataConsentWithdrawnEvent(
        event.eventId(),
        event.userId(),
        event.consentVersion(),
        event.consentRecordVersion(),
        event.occurredAt(),
        event.traceId());
  }
}
