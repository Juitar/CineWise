package com.miaoyu.ticket.profile.application;

import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同意撤回后立即关闭画像并清理当前摘要。
 * 事件 ID 借用画像幂等表去重，重复通知不会重复延长清理时间，也不会重新打开画像。
 */
@Service
public class ProfileConsentWithdrawalHandler {
  private static final String OPERATION = "CONSENT_WITHDRAWN";
  private final ProfileDataConsentQuery consentQuery;
  private final ProfilePreferenceRepository preferenceRepository;
  private final ProfileTagRepository tagRepository;
  private final ProfileWriteRequestRepository writeRequestRepository;
  private final ProfileSummaryCache summaryCache;
  private final BusinessIdGenerator idGenerator;
  private final Clock clock;

  public ProfileConsentWithdrawalHandler(
      ProfileDataConsentQuery consentQuery,
      ProfilePreferenceRepository preferenceRepository,
      ProfileTagRepository tagRepository,
      ProfileWriteRequestRepository writeRequestRepository,
      ProfileSummaryCache summaryCache,
      BusinessIdGenerator idGenerator,
      Clock clock) {
    this.consentQuery = consentQuery;
    this.preferenceRepository = preferenceRepository;
    this.tagRepository = tagRepository;
    this.writeRequestRepository = writeRequestRepository;
    this.summaryCache = summaryCache;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  /**
   * 处理 C 提交后的画像同意撤回通知。
   *
   * 撤回接口先落库，再由 outbox 投递本事件，因此事件到达时不能假设页面仍处于撤回状态。
   * 用户可能已经在另一台设备或同一页面快速重新同意；此时继续按旧事件清理，会删除新状态下的数据。
   * 当前同意状态为已同意时，仅写入幂等记录，确保 outbox 可以标记投递完成且不会反复处理。
   * 当前仍未同意时，才关闭偏好、删除标签并失效摘要缓存。
   *
   * 事件 ID 是处理去重键，不以用户 ID 去重，避免一次新的撤回被旧事件的处理记录吞掉。
   * 同意版本用于生成幂等请求摘要，便于区分同一用户不同生命周期内的撤回事件。
   * 该处理器不重新开启任何画像设置；重新同意后的偏好选择仍由用户单独控制。
   *
   * @param event 已提交的撤回通知，只包含清理所需的最小标识和版本信息
   */
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(ProfileDataConsentWithdrawnEvent event) {
    // 无法形成可靠去重键或用户归属时，不能执行可能影响其他画像数据的清理。
    if (event.eventId() == null || event.eventId().isBlank() || event.userId() <= 0) {
      return;
    }
    String hash = ProfileRequestHasher.sha256(event.eventId() + "|" + event.consentVersion());
    if (writeRequestRepository.findByUserIdAndOperationAndIdempotencyKey(
        event.userId(), OPERATION, event.eventId()).isPresent()) {
      return;
    }
    LocalDateTime now = utcNow();
    // 先查同意状态，再决定是否执行清理，避免把过期撤回事件当成当前用户意图。
    // 这里使用 C 提供的查询边界，不直接读取认证模块的持久化表。
    // 已重新同意代表用户开启了新的数据生命周期，旧事件只能完成投递记录。
    // 未同意时才执行关闭偏好和删除标签，保证撤回的隐私语义仍然有效。
    // 幂等记录与清理分支都使用同一个 eventId，重试不会产生第二次业务动作。
    // 当前实现不创建新幂等键，也不通过重试绕过并发版本检查。
    // 撤回事件可能因 outbox 重试晚于重新同意到达；已重新同意时不能再清理新状态。
    // 保留处理记录后，重复投递会在上面的去重判断处结束，不会不断查询或清理。
    if (consentQuery.findByUserId(event.userId()).granted()) {
      writeRequestRepository.insert(new ProfileWriteRequestRepository.NewRequest(
          idGenerator.nextId(), event.userId(), OPERATION, event.eventId(), hash, 200, "{}", now, now.plusDays(30)));
      return;
    }
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
