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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受控行为写入端口，只接受已鉴权的最小字段。
 * 事件表先按 eventId 去重，再按用户、事件和目标的 24 小时窗口去重；写入失败会回滚，调用方可安全重试。
 *
 * <p>处理边界：
 * <ul>
 *   <li>当前用户的普通行为只能从认证上下文取得用户 ID。</li>
 *   <li>支付行为只接受 A 已提交后的 PaymentSucceededEvent。</li>
 *   <li>支付事件只保存 eventId、用户、订单、场次、版本和发生时间。</li>
 *   <li>支付事件不保存 cinemaId、cinemaArea、开场时间或其他出行字段。</li>
 *   <li>支付事件不能仅根据 showId 推导影片、影院或影厅偏好。</li>
 *   <li>没有可靠映射时，支付事件只作为 SHOW 行为摘要保留。</li>
 *   <li>用户未同意画像保存时，不创建设置、事件、标签或缓存。</li>
 *   <li>用户关闭个性化后，不采集新行为，也不补采历史行为。</li>
 *   <li>相同 eventId 必须返回第一次处理的 accepted 和 changed 结果。</li>
 *   <li>相同 eventId 不得再次更新标签权重、版本或缓存。</li>
 *   <li>不同 eventId 命中同一 24 小时窗口时仍保存最小事件摘要。</li>
 *   <li>窗口内的后续事件不得再次累计长期标签权重。</li>
 *   <li>首次设置创建依赖数据库唯一约束，避免并发创建多行。</li>
 *   <li>标签修改与偏好版本推进必须位于同一短事务中。</li>
 *   <li>版本条件更新失败时回滚事件写入，供上游按原 eventId 重试。</li>
 *   <li>唯一键冲突时只查询已经提交的事件结果，不重放写操作。</li>
 *   <li>事件结果只保存 changed 布尔值，不保存原始请求 payload。</li>
 *   <li>事件表不保存完整对话、邮箱、Cookie、JWT 或精确位置。</li>
 *   <li>行为标签只允许使用已定义的类型、来源、状态和极性。</li>
 *   <li>单次低权重行为不足以创建长期标签时，changed 仍反映归一化结果。</li>
 *   <li>行为标签的权重受固定上限约束，不随重复事件无限增加。</li>
 *   <li>行为标签具有固定有效期，后续由衰减任务按版本条件处理。</li>
 *   <li>缓存只在画像状态实际改变后失效，避免无意义删除。</li>
 *   <li>该服务不访问订单、支付、认证或推荐模块的持久化层。</li>
 *   <li>该服务不发布 SSE、不写 agent_* 表、不调用模型。</li>
 *   <li>调用方不能伪造 PAID_ORDER 的用户、订单或场次事实。</li>
 *   <li>CLICK、FAVORITE 和 NOT_INTERESTED 只能关联 MOVIE 目标。</li>
 *   <li>ACCEPT_PLAN 和 REJECT_PLAN 只能关联 B 提供的稳定 PLAN ID。</li>
 *   <li>PAID_ORDER 只能关联 A 提供的 SHOW ID。</li>
 *   <li>事件 ID 为空、过长或目标为空时必须拒绝。</li>
 *   <li>非法目标类型必须拒绝，不能降级为其他标签类型。</li>
 *   <li>普通行为写入不接受请求体携带的 userId。</li>
 *   <li>支付写入不依赖消费线程中的登录上下文。</li>
 *   <li>事件查询发生在用户偏好行锁保护的归一化流程中。</li>
 *   <li>读取到已有事件后不再读取标签，减少隐私数据暴露范围。</li>
 *   <li>处理失败不会回滚 A 已经完成的支付事务。</li>
 *   <li>支付监听器在支付事务提交后调用本服务。</li>
 *   <li>事件时间统一由注入 Clock 转为 UTC，保证测试可复现。</li>
 *   <li>标签有效期和事件时间不使用散落的系统 now 调用。</li>
 *   <li>行为来源固定为 BEHAVIOR，不能伪装成用户手工标签。</li>
 *   <li>手工和对话标签不参与本服务的自动衰减或权重修改。</li>
 *   <li>标签权重变化后才推进偏好版本，以隔离 Redis 摘要键。</li>
 *   <li>画像摘要缓存不可用时由查询侧回退，不改变写入规则。</li>
 *   <li>事件表的 90 天清理由受限批任务完成，不在写请求中扫描历史。</li>
 *   <li>清理任务失败不影响新的受控行为写入。</li>
 *   <li>审计日志只记录操作结果和 traceId，不记录标签完整值。</li>
 *   <li>同意撤回后由专用处理器关闭开关并安排分期清理。</li>
 *   <li>本服务不承担账户删除通知或账户删除清理。</li>
 *   <li>没有 C 的正式同意查询时，fallback 必须拒绝写入。</li>
 *   <li>没有 B 的确认输入时，不接收长期对话偏好写入。</li>
 *   <li>没有 A 的可靠影院 ID 映射时，不从支付事件生成影院标签。</li>
 *   <li>推荐模块只能读取摘要，不能通过本服务修改画像。</li>
 *   <li>返回结果仅说明本次是否接受及是否改变画像状态。</li>
 *   <li>返回结果不暴露事件表主键、订单细节或用户隐私字段。</li>
 *   <li>相同 eventId 的并发调用以数据库唯一约束作为最终保障。</li>
 *   <li>进程内锁和 Redis 锁都不能代替数据库唯一约束。</li>
 *   <li>发生冲突后读取结果而非再次插入，避免重复副作用。</li>
 *   <li>事件重放不会延长行为标签的有效期。</li>
 *   <li>事件重放不会恢复已关闭的个性化开关。</li>
 *   <li>事件重放不会绕过新的同意状态校验。</li>
 *   <li>支付事件最小摘要不能被当作票务事实对外展示。</li>
 *   <li>推荐结果是否采用画像由推荐模块单独说明。</li>
 * </ul>
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

  /**
   * B 在用户最终接受已保存方案后调用；这里只记录最小 PLAN 反馈，不把 planId 当成偏好标签值。
   * 方案中的影片类型、影院等事实没有经过 D 的可靠映射时，不能凭 UUID 生成长期画像标签。
   */
  @Transactional
  public RecordResult recordPlanAccepted(String eventId, String planId, LocalDateTime occurredAt) {
    return record(new BehaviorCommand(eventId, ProfileBehaviorEventType.ACCEPT_PLAN,
        ProfileBehaviorTargetType.PLAN, planId, null, null, occurredAt));
  }

  /** B 在用户最终拒绝已保存方案后调用；拒绝反馈同样只保留可去重的最小事件摘要。 */
  @Transactional
  public RecordResult recordPlanRejected(String eventId, String planId, LocalDateTime occurredAt) {
    return record(new BehaviorCommand(eventId, ProfileBehaviorEventType.REJECT_PLAN,
        ProfileBehaviorTargetType.PLAN, planId, null, null, occurredAt));
  }

  /** A 的支付事件是受信任来源；消费线程没有用户登录上下文，只使用事件中的用户和订单字段。 */
  @Transactional
  public RecordResult recordPayment(PaymentSucceededEvent event) {
    long userId = parsePositiveId(event.userId());
    BehaviorCommand command = new BehaviorCommand(
        event.eventId(), ProfileBehaviorEventType.PAID_ORDER, ProfileBehaviorTargetType.SHOW,
        event.showId(), null, null,
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
    var existing = eventRepository.findByEventId(command.eventId());
    if (existing.isPresent()) {
      return replay(existing.get());
    }
    LocalDateTime now = utcNow();
    boolean alreadyNormalized = eventRepository.existsInWindow(
        userId, command.eventType(), command.targetType(), command.targetId(), now.minusHours(24));
    boolean changed = !alreadyNormalized && command.tagType() != null;
    try {
      eventRepository.insert(new ProfileBehaviorEventRepository.NewEvent(
          idGenerator.nextId(), command.eventId(), userId, command.eventType(), command.targetType(),
          command.targetId(), orderId, orderVersion, command.occurredAt(), now, changed));
    } catch (DuplicateKeyException duplicate) {
      // 并发方先提交同一 eventId 时，只读取其已保存的最小处理结果，绝不再次累计标签。
      return eventRepository.findByEventId(command.eventId())
          .map(this::replay)
          .orElseThrow(() -> duplicate);
    }
    // 同一窗口的不同 eventId 仍要保留最小事件摘要，只是不再次改变长期标签。
    if (!changed) {
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
        || command.occurredAt() == null
        || (requiresTag(command.eventType())
            && (command.tagType() == null || command.tagValue() == null))
        || (command.eventType() == ProfileBehaviorEventType.PAID_ORDER
            && (command.tagType() != null || command.tagValue() != null))) {
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
    if ((command.eventType() == ProfileBehaviorEventType.ACCEPT_PLAN
        || command.eventType() == ProfileBehaviorEventType.REJECT_PLAN)
        && !isCanonicalLowerUuid(command.targetId())) {
      throw new BusinessException(ProfileErrorCode.INVALID_EVENT);
    }
  }

  /** 只有影片行为具备 D 可直接使用的标签证据；PLAN 反馈先只记录事件。 */
  private boolean requiresTag(ProfileBehaviorEventType eventType) {
    return eventType == ProfileBehaviorEventType.CLICK
        || eventType == ProfileBehaviorEventType.FAVORITE
        || eventType == ProfileBehaviorEventType.NOT_INTERESTED;
  }

  /** 方案 ID 必须使用 B 生成的标准小写 UUID，避免把临时文本或模型字段写入行为记录。 */
  private boolean isCanonicalLowerUuid(String value) {
    try {
      return UUID.fromString(value).toString().equals(value);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private LocalDateTime utcNow() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  /** 重放只使用事件表保存的最小处理标记，不重新读取或累计任何画像标签。 */
  private RecordResult replay(ProfileBehaviorEventRepository.Snapshot event) {
    return new RecordResult(true, event.changed());
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
