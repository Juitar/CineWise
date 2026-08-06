package com.miaoyu.ticket.profile.infrastructure.persistence;

import com.miaoyu.ticket.profile.application.ProfileBehaviorEventRepository;
import com.miaoyu.ticket.profile.domain.ProfileBehaviorEventType;
import com.miaoyu.ticket.profile.domain.ProfileBehaviorTargetType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis 行适配为领域枚举，业务代码不直接处理数据库字符串。 */
@Repository
public class MybatisProfileBehaviorEventRepository implements ProfileBehaviorEventRepository {
  private final ProfileBehaviorEventPersistenceMapper mapper;

  public MybatisProfileBehaviorEventRepository(ProfileBehaviorEventPersistenceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Snapshot> findByEventId(String eventId) {
    return Optional.ofNullable(mapper.findByEventId(eventId)).map(this::toSnapshot);
  }

  @Override
  public boolean existsInWindow(
      long userId,
      ProfileBehaviorEventType eventType,
      ProfileBehaviorTargetType targetType,
      String targetId,
      LocalDateTime since) {
    return mapper.existsInWindow(userId, eventType.name(), targetType.name(), targetId, since);
  }

  @Override
  public void insert(NewEvent event) {
    if (mapper.insert(event) != 1) {
      throw new IllegalStateException("画像行为事件写入行数异常");
    }
  }

  @Override
  public int cleanupBefore(LocalDateTime before, int limit) {
    return mapper.cleanupBefore(before, limit);
  }

  private Snapshot toSnapshot(ProfileBehaviorEventRow row) {
    return new Snapshot(
        row.eventId(),
        row.userId(),
        ProfileBehaviorEventType.valueOf(row.eventType()),
        ProfileBehaviorTargetType.valueOf(row.targetType()),
        row.targetId(),
        row.occurredAt(),
        row.changed());
  }
}
