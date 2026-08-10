package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.common.observability.TraceIdHolder;
import com.miaoyu.ticket.profile.application.ProfileDataConsentQuery;
import com.miaoyu.ticket.profile.application.ProfileDataConsentSnapshot;
import com.miaoyu.ticket.profile.application.ProfileDataConsentWithdrawnEvent;
import com.miaoyu.ticket.profile.application.ProfileDataConsentWithdrawalGuard;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** C 管理独立的画像数据保存同意，注册隐私同意和个性化开关均不能替代。 */
@Service
public class ProfileDataConsentService implements ProfileDataConsentQuery, ProfileDataConsentWithdrawalGuard {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProfileDataConsentService.class);
  private final ProfileDataConsentRepository consentRepository;
  private final ProfileDataConsentOutboxRepository outboxRepository;
  private final ProfileDataConsentOutboxDeliveryService deliveryService;
  private final BusinessIdGenerator idGenerator;
  private final Clock clock;

  public ProfileDataConsentService(
      ProfileDataConsentRepository consentRepository,
      ProfileDataConsentOutboxRepository outboxRepository,
      ProfileDataConsentOutboxDeliveryService deliveryService,
      BusinessIdGenerator idGenerator,
      Clock clock) {
    this.consentRepository = consentRepository;
    this.outboxRepository = outboxRepository;
    this.deliveryService = deliveryService;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  public ProfileDataConsentSnapshot findByUserId(long userId) {
    if (userId <= 0) {
      return ProfileDataConsentSnapshot.notGranted();
    }
    return consentRepository.findByUserId(userId).map(this::toSnapshot)
        .orElseGet(ProfileDataConsentSnapshot::notGranted);
  }

  /** 在 C 的行锁内校验撤回事件版本，并执行 D 提供的清理动作。 */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean executeIfCurrent(ProfileDataConsentWithdrawnEvent event, Runnable cleanup) {
    ProfileDataConsentRepository.ConsentRecord current =
        consentRepository.findByUserIdForUpdate(event.userId()).orElse(null);
    if (current == null
        || current.status() != ProfileDataConsentRepository.Status.WITHDRAWN
        || current.consentVersion() != event.consentVersion()
        || current.recordVersion() != event.consentRecordVersion()) {
      return false;
    }
    cleanup.run();
    return true;
  }

  /** 首次同意创建记录，重复同意不递增版本；撤回后重新同意才递增 consentVersion。 */
  @Transactional
  public ProfileDataConsentSnapshot grant(long userId, String privacyPolicyVersion) {
    requireUserId(userId);
    String policyVersion = requirePolicyVersion(privacyPolicyVersion);
    Instant now = clock.instant();
    ProfileDataConsentRepository.ConsentRecord existing = consentRepository.findByUserId(userId).orElse(null);
    if (existing == null) {
      try {
        consentRepository.insertGranted(idGenerator.nextId(), userId, policyVersion, now);
      } catch (DuplicateKeyException exception) {
        throw new BusinessException(AuthErrorCode.PROFILE_DATA_CONSENT_CONFLICT);
      }
    } else if (existing.status() == ProfileDataConsentRepository.Status.WITHDRAWN
        && !consentRepository.regrant(userId, existing.recordVersion(), policyVersion, now)) {
      throw new BusinessException(AuthErrorCode.PROFILE_DATA_CONSENT_CONFLICT);
    }
    return consentRepository.findByUserId(userId).map(this::toSnapshot)
        .orElseThrow(() -> new IllegalStateException("画像数据保存同意记录写入后不存在"));
  }

  /** CAS 撤回和唯一 outbox 插入位于同一事务；任一步异常都会整体回滚。 */
  @Transactional
  public WithdrawalResult withdraw(long userId) {
    requireUserId(userId);
    ProfileDataConsentRepository.ConsentRecord existing = consentRepository.findByUserId(userId).orElse(null);
    if (existing == null || existing.status() == ProfileDataConsentRepository.Status.WITHDRAWN) {
      return new WithdrawalResult(false, null);
    }
    Instant now = clock.instant();
    if (!consentRepository.withdraw(userId, existing.recordVersion(), now)) {
      throw new BusinessException(AuthErrorCode.PROFILE_DATA_CONSENT_CONFLICT);
    }
    String eventId = UUID.randomUUID().toString();
    String traceId = TraceIdHolder.currentTraceId();
    if (traceId == null || traceId.isBlank()) {
      traceId = eventId;
    }
    outboxRepository.insert(new ProfileDataConsentOutboxRepository.OutboxEvent(
        eventId,
        userId,
        existing.consentVersion(),
        existing.recordVersion() + 1,
        now,
        traceId,
        ProfileDataConsentOutboxRepository.Status.PENDING,
        0,
        now));
    registerImmediateDelivery(eventId);
    return new WithdrawalResult(true, eventId);
  }

  private void registerImmediateDelivery(String eventId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException("撤回必须在事务同步开启时执行");
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        try {
          deliveryService.deliverPending(eventId);
        } catch (RuntimeException exception) {
          LOGGER.warn("画像同意撤回提交后立即投递失败，等待 outbox 调度恢复，eventId={}", eventId, exception);
        }
      }
    });
  }

  private ProfileDataConsentSnapshot toSnapshot(ProfileDataConsentRepository.ConsentRecord record) {
    return new ProfileDataConsentSnapshot(
        record.status() == ProfileDataConsentRepository.Status.GRANTED,
        record.consentVersion(),
        record.recordVersion(),
        record.grantedAt(),
        record.withdrawnAt());
  }

  private void requireUserId(long userId) {
    if (userId <= 0) {
      throw new BusinessException(AuthErrorCode.INVALID_PARAMETER);
    }
  }

  private String requirePolicyVersion(String privacyPolicyVersion) {
    String normalized = Objects.requireNonNullElse(privacyPolicyVersion, "").trim();
    if (normalized.isEmpty() || normalized.length() > 32) {
      throw new BusinessException(AuthErrorCode.INVALID_PARAMETER);
    }
    return normalized;
  }

  public record WithdrawalResult(boolean changed, String eventId) { }
}
