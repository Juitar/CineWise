package com.miaoyu.ticket.profile.application;

import com.miaoyu.ticket.profile.domain.BehaviorTagDecay;
import com.miaoyu.ticket.profile.domain.ProfileTagStatus;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 行为标签衰减任务：每批按标签版本写回，重复执行不会覆盖较新的用户行为。 */
public class ProfileDecayJob {
  private static final int BATCH_SIZE = 100;
  private final ProfileTagRepository tagRepository;
  private final Clock clock;

  public ProfileDecayJob(ProfileTagRepository tagRepository, Clock clock) {
    this.tagRepository = tagRepository;
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
  public int executeOnce() {
    Instant now = clock.instant();
    LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
    int changed = 0;
    for (ProfileTagRepository.Snapshot tag : findDueTags()) {
      Instant lastUpdatedAt = tag.updatedAt().toInstant(ZoneOffset.UTC);
      boolean expired = BehaviorTagDecay.isExpired(lastUpdatedAt, now);
      if (tagRepository.updateBehaviorState(
          tag.id(),
          tag.version(),
          expired ? tag.weight() : BehaviorTagDecay.decay(tag.weight(), lastUpdatedAt, now),
          expired ? ProfileTagStatus.EXPIRED : ProfileTagStatus.ACTIVE,
          tag.expiresAt(),
          nowUtc)) {
        changed++;
      }
    }
    return changed;
  }
}
