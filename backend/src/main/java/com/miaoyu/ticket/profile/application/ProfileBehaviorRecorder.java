package com.miaoyu.ticket.profile.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.profile.domain.ProfileBehaviorEventType;
import com.miaoyu.ticket.profile.domain.ProfileBehaviorNormalizer;
import com.miaoyu.ticket.profile.domain.ProfileBehaviorRule;
import com.miaoyu.ticket.profile.domain.ProfileBehaviorTargetType;
import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagSource;
import com.miaoyu.ticket.profile.domain.ProfileTagStatus;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import com.miaoyu.ticket.profile.domain.ProfileTagValue;
import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受控行为写入端口，只接受已鉴权的最小字段。
 * 事件表先按 eventId 去重，再按用户、事件和目标的 24 小时窗口去重；写入失败会回滚，调用方可安全重试。
 */
@Service
public class ProfileBehaviorRecorder {
  private final CurrentUserAccessor currentUserAccessor;
  private final ProfileDataConsentQuery consentQuery;
  private final ProfilePreferenceRepository preferenceRepository;
  private final ProfileBehaviorEventRepository eventRepository;
  private final ProfileTagRepository tagRepository;
  private final ProfileSummaryCache summaryCache;
  private final BusinessIdGenerator idGenerator;
  private final Clock clock;

  public ProfileBehaviorRecorder(
      CurrentUserAccessor currentUserAccessor,
      ProfileDataConsentQuery consentQuery,
      ProfilePreferenceRepository preferenceRepository,
      ProfileBehaviorEventRepository eventRepository,
      ProfileTagRepository tagRepository,
      ProfileSummaryCache summaryCache,
      BusinessIdGenerator idGenerator,
      Clock clock) {
    this.currentUserAccessor = currentUserAccessor;
    this.consentQuery = consentQuery;
    this.preferenceRepository = preferenceRepository;
    this.eventRepository = eventRepository;
    this.tagRepository = tagRepository;
    this.summaryCache = summaryCache;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Transactional
  public RecordResult record(BehaviorCommand command) {
    long userId = currentUserAccessor.requireCurrentUserId();
    return recordForUser(userId, command, null, null);
  }

  /** A 的支付事件是受信任来源；消费线程没有用户登录上下文，只使用事件中的用户和订单字段。 */
  @Transactional
  public RecordResult recordPayment(PaymentSucceededEvent event) {
    long userId = parsePositiveId(event.userId());
    BehaviorCommand command = new BehaviorCommand(
        event.eventId(), ProfileBehaviorEventType.PAID_ORDER, ProfileBehaviorTargetType.SHOW,
        event.showId(), ProfileTagType.CINEMA, event.cinemaArea(),
        event.occurredAt().toLocalDateTime());
    return recordForUser(userId, command, parsePositiveId(event.orderId()), event.orderVersion());
  }

  private RecordResult recordForUser(long userId, BehaviorCommand command, Long orderId, Long orderVersion) {
    if (!consentQuery.isGranted(userId)) {
      throw new BusinessException(ProfileErrorCode.PROFILE_DATA_CONSENT_REQUIRED);
    }
    validate(command);
    ProfilePreferenceRepository.Snapshot preference = preferenceRepository.findByUserIdForUpdate(userId)
        .orElseGet(() -> createPreference(userId));
    if (!preference.personalizationEnabled()) {
      return new RecordResult(false, false);
    }
    if (eventRepository.findByEventId(command.eventId()).isPresent()) {
      return new RecordResult(true, false);
    }
    LocalDateTime now = utcNow();
    boolean alreadyNormalized = eventRepository.existsInWindow(
        userId, command.eventType(), command.targetType(), command.targetId(), now.minusHours(24));
    eventRepository.insert(new ProfileBehaviorEventRepository.NewEvent(
        idGenerator.nextId(), command.eventId(), userId, command.eventType(), command.targetType(),
        command.targetId(), orderId, orderVersion, command.occurredAt(), now));
    // 同一窗口的不同 eventId 仍要保留最小事件摘要，只是不再次改变长期标签。
    if (alreadyNormalized) {
      return new RecordResult(true, false);
    }
    ProfileTagRepository.Snapshot tag = tagRepository
        .findActiveByKey(userId, command.tagType(), command.tagValue(), ProfileTagSource.BEHAVIOR)
        .orElse(null);
    BigDecimal delta = ProfileBehaviorNormalizer.weightOf(command.eventType());
    if (tag == null) {
      if (ProfileBehaviorRule.shouldCreateOrUpdateTag(delta)) {
        tagRepository.insert(new ProfileTagRepository.NewTag(
            idGenerator.nextId(), userId, command.tagType(), new ProfileTagValue(command.tagValue()).value(),
            delta.signum() >= 0 ? ProfileTagPolarity.LIKE : ProfileTagPolarity.DISLIKE,
            ProfileBehaviorRule.capWeight(delta.abs()), ProfileTagSource.BEHAVIOR, new BigDecimal("0.500"),
            ProfileTagStatus.ACTIVE, now.plusDays(90), now));
      }
    } else {
      BigDecimal accumulated = ProfileBehaviorRule.capWeight(tag.weight().add(delta.abs()));
      if (ProfileBehaviorRule.shouldCreateOrUpdateTag(accumulated)
          && !tagRepository.updateBehaviorWeight(tag.id(), tag.version(), accumulated,
              delta.signum() >= 0 ? ProfileTagPolarity.LIKE : ProfileTagPolarity.DISLIKE,
              now.plusDays(90), now)) {
        throw new BusinessException(ProfileErrorCode.PROFILE_VERSION_CONFLICT);
      }
    }
    if (!preferenceRepository.incrementVersionIfMatches(userId, preference.version(), now)) {
      throw new BusinessException(ProfileErrorCode.PROFILE_VERSION_CONFLICT);
    }
    summaryCache.invalidateUser(userId);
    return new RecordResult(true, true);
  }

  private long parsePositiveId(String value) {
    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0) {
        throw new NumberFormatException();
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw new BusinessException(ProfileErrorCode.INVALID_EVENT);
    }
  }

  private ProfilePreferenceRepository.Snapshot createPreference(long userId) {
    LocalDateTime now = utcNow();
    try {
      preferenceRepository.insertDefault(userId, now);
    } catch (DuplicateKeyException ignored) {
      // 并发首次采集只能有一个插入成功，失败方重新锁定同一行继续处理。
    }
    return preferenceRepository.findByUserIdForUpdate(userId).orElseThrow();
  }

  private void validate(BehaviorCommand command) {
    if (command.eventId() == null || command.eventId().isBlank() || command.eventId().length() > 64
        || command.targetId() == null || command.targetId().isBlank()
        || command.tagType() == null || command.tagValue() == null) {
      throw new BusinessException(ProfileErrorCode.INVALID_EVENT);
    }
    boolean validTarget = switch (command.eventType()) {
      case CLICK, FAVORITE, NOT_INTERESTED -> command.targetType() == ProfileBehaviorTargetType.MOVIE;
      case ACCEPT_PLAN, REJECT_PLAN -> command.targetType() == ProfileBehaviorTargetType.PLAN;
      case PAID_ORDER -> command.targetType() == ProfileBehaviorTargetType.SHOW;
    };
    if (!validTarget) {
      throw new BusinessException(ProfileErrorCode.INVALID_EVENT);
    }
  }

  private LocalDateTime utcNow() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  public record BehaviorCommand(
      String eventId,
      ProfileBehaviorEventType eventType,
      ProfileBehaviorTargetType targetType,
      String targetId,
      ProfileTagType tagType,
      String tagValue,
      LocalDateTime occurredAt) { }

  public record RecordResult(boolean accepted, boolean changed) { }
}
