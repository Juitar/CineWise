package com.miaoyu.ticket.profile.application;

import java.time.LocalDateTime;
import java.util.Optional;

/** 用户画像设置的应用层持久化端口，后续 MyBatis 适配器只能实现本接口。 */
public interface ProfilePreferenceRepository {
  Optional<Snapshot> findByUserId(long userId);

  /** 仅由同一画像短事务调用，保证首次创建和行为归一化可以串行化。 */
  Optional<Snapshot> findByUserIdForUpdate(long userId);

  /** 仅在画像保存同意已校验后的事务内创建默认设置。 */
  void insertDefault(long userId, LocalDateTime now);

  /**
   * 标签内容变化必须推进持久化版本，使此前 Redis 摘要键立刻不可达。
   * 返回 false 说明用户设置不存在或已删除，调用方必须回滚标签写入。
   */
  boolean incrementVersion(long userId, LocalDateTime updatedAt);

  record Snapshot(
      long userId, boolean personalizationEnabled, long version, LocalDateTime updatedAt) { }
}
