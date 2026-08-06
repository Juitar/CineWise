package com.miaoyu.ticket.profile.infrastructure.persistence;

import com.miaoyu.ticket.profile.application.ProfilePreferenceRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 将 user_preference SQL 行转换为应用层快照，避免 Mapper 泄漏到业务规则。 */
@Repository
public class MybatisProfilePreferenceRepository implements ProfilePreferenceRepository {
  private final ProfilePreferencePersistenceMapper mapper;

  public MybatisProfilePreferenceRepository(ProfilePreferencePersistenceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Snapshot> findByUserId(long userId) {
    return Optional.ofNullable(mapper.findByUserId(userId)).map(this::toSnapshot);
  }

  @Override
  public Optional<Snapshot> findByUserIdForUpdate(long userId) {
    return Optional.ofNullable(mapper.findByUserIdForUpdate(userId)).map(this::toSnapshot);
  }

  @Override
  public void insertDefault(long userId, LocalDateTime now) {
    if (mapper.insertDefault(userId, now) != 1) {
      // 默认设置必须精确写入一行，否则后续版本校验和行为归一化都不能成立。
      throw new IllegalStateException("默认画像设置写入行数异常");
    }
  }

  @Override
  public boolean incrementVersion(long userId, LocalDateTime updatedAt) {
    return mapper.incrementVersion(userId, updatedAt) == 1;
  }

  private Snapshot toSnapshot(ProfilePreferenceRow row) {
    return new Snapshot(row.userId(), row.personalizationEnabled(), row.version(), row.updatedAt());
  }
}
