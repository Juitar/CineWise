package com.miaoyu.ticket.profile.application;

import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同意撤回后立即关闭画像并清理当前摘要。
 * 事件 ID 借用画像幂等表去重，重复通知不会重复延长清理时间，也不会重新打开画像。
 */
@Service
public class ProfileConsentWithdrawalHandler {
  private static final String OPERATION = "CONSENT_WITHDRAWN";
  private final ProfilePreferenceRepository preferenceRepository;
  private final ProfileTagRepository tagRepository;
  private final ProfileWriteRequestRepository writeRequestRepository;
  private final ProfileSummaryCache summaryCache;
  private final BusinessIdGenerator idGenerator;
  private final Clock clock;

  public ProfileConsentWithdrawalHandler(
      ProfilePreferenceRepository preferenceRepository,
      ProfileTagRepository tagRepository,
      ProfileWriteRequestRepository writeRequestRepository,
      ProfileSummaryCache summaryCache,
      BusinessIdGenerator idGenerator,
      Clock clock) {
    this.preferenceRepository = preferenceRepository;
    this.tagRepository = tagRepository;
    this.writeRequestRepository = writeRequestRepository;
    this.summaryCache = summaryCache;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @EventListener
  @Transactional
  public void handle(ProfileDataConsentWithdrawnEvent event) {
    if (event.eventId() == null || event.eventId().isBlank() || event.userId() <= 0) {
      return;
    }
    String hash = ProfileRequestHasher.sha256(event.eventId() + "|" + event.consentVersion());
    if (writeRequestRepository.findByUserIdAndOperationAndIdempotencyKey(
        event.userId(), OPERATION, event.eventId()).isPresent()) {
      return;
    }
    LocalDateTime now = utcNow();
    preferenceRepository.disable(event.userId(), now);
    tagRepository.softDeleteAll(event.userId(), now);
    summaryCache.invalidateUser(event.userId());
    writeRequestRepository.insert(new ProfileWriteRequestRepository.NewRequest(
        idGenerator.nextId(), event.userId(), OPERATION, event.eventId(), hash, 200, "{}", now, now.plusDays(30)));
  }

  private LocalDateTime utcNow() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
