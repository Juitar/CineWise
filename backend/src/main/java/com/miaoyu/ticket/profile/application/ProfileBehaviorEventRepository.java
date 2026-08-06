package com.miaoyu.ticket.profile.application;

import com.miaoyu.ticket.profile.domain.ProfileBehaviorEventType;
import com.miaoyu.ticket.profile.domain.ProfileBehaviorTargetType;
import java.time.LocalDateTime;
import java.util.Optional;

/** 行为事件持久化端口，24 小时窗口由应用服务在已锁定用户设置后调用。 */
public interface ProfileBehaviorEventRepository {
  Optional<Snapshot> findByEventId(String eventId);

  boolean existsInWindow(
      long userId,
      ProfileBehaviorEventType eventType,
      ProfileBehaviorTargetType targetType,
      String targetId,
      LocalDateTime since);

  void insert(NewEvent event);

  default int cleanupBefore(LocalDateTime before, int limit) {
    return 0;
  }

  record NewEvent(
      long id,
      String eventId,
      long userId,
      ProfileBehaviorEventType eventType,
      ProfileBehaviorTargetType targetType,
      String targetId,
      Long orderId,
      Long orderVersion,
      LocalDateTime occurredAt,
      LocalDateTime createdAt,
      boolean changed) { }

  record Snapshot(
      String eventId,
      long userId,
      ProfileBehaviorEventType eventType,
      ProfileBehaviorTargetType targetType,
      String targetId,
      LocalDateTime occurredAt,
      boolean changed) { }
}
