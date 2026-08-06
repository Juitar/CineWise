package com.miaoyu.ticket.profile.application;

import com.miaoyu.ticket.profile.domain.BehaviorTagDecay;
import com.miaoyu.ticket.profile.domain.ProfileTagStatus;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

/** 行为标签衰减任务：每批按标签版本写回，重复执行不会覆盖较新的用户行为。 */
@Service
public class ProfileDecayJob {
  private static final int BATCH_SIZE = 100;
  private final ProfileTagRepository tagRepository;
  private final ProfilePreferenceRepository preferenceRepository;
  private final ProfileSummaryCache summaryCache;
  private final Clock clock;

  public ProfileDecayJob(
      ProfileTagRepository tagRepository,
      ProfilePreferenceRepository preferenceRepository,
      ProfileSummaryCache summaryCache,
      Clock clock) {
    this.tagRepository = tagRepository;
    this.preferenceRepository = preferenceRepository;
    this.summaryCache = summaryCache;
    this.clock = clock;
  }

  public List<ProfileTagRepository.Snapshot> findDueTags() {
    LocalDateTime before = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(30);
    return tagRepository.findBehaviorTagsDueBefore(before, BATCH_SIZE);
  }

  /**
   * 30 天后降低权重，90 天后直接标记过期；更新时推进 update_time，保证同一窗口只处理一次。
   * 返回真正写成功的数量，版本冲突表示已有新行为，不作为任务失败重试旧数据。
   */
  @Transactional
  @Scheduled(cron = "${cinewise.profile.decay-cron:0 15 2 * * *}")
  public int executeOnce() {
    Instant now = clock.instant();
    LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
    int changed = 0;
    for (ProfileTagRepository.Snapshot tag : findDueTags()) {
      Instant lastDecayAt = tag.updatedAt().toInstant(ZoneOffset.UTC);
      // expiresAt 是最后行为时间确定的固定终止时间，不能被任务自身 update_time 覆盖。
      boolean expired =
          tag.expiresAt() == null
              || BehaviorTagDecay.isExpiredAt(tag.expiresAt().toInstant(ZoneOffset.UTC), now);
      if (tagRepository.updateBehaviorState(
          tag.id(),
          tag.version(),
          expired ? tag.weight() : BehaviorTagDecay.decay(tag.weight(), lastDecayAt, now),
          expired ? ProfileTagStatus.EXPIRED : ProfileTagStatus.ACTIVE,
          tag.expiresAt(),
          nowUtc)) {
        if (!preferenceRepository.incrementVersion(tag.userId(), nowUtc)) {
          // 标签和版本必须同一事务成功；没有设置记录时不能留下与缓存版本脱节的标签变更。
          throw new IllegalStateException("画像设置不存在，不能推进摘要版本");
        }
        // Redis 删除只用于回收旧键；正确性由上面已持久化的版本推进保证。
        summaryCache.invalidateUser(tag.userId());
        changed++;
      }
    }
    return changed;
  }
}
