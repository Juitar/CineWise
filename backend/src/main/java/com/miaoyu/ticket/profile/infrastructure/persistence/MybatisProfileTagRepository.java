package com.miaoyu.ticket.profile.infrastructure.persistence;

import com.miaoyu.ticket.profile.application.ProfileTagRepository;
import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagSource;
import com.miaoyu.ticket.profile.domain.ProfileTagStatus;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 标签 SQL 适配器将数据库字符串恢复为受限领域枚举。 */
@Repository
public class MybatisProfileTagRepository implements ProfileTagRepository {
  private final ProfileTagPersistenceMapper mapper;

  public MybatisProfileTagRepository(ProfileTagPersistenceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void insert(NewTag tag) {
    if (mapper.insert(tag) != 1) {
      throw new IllegalStateException("画像标签写入行数异常");
    }
  }

  @Override
  public Optional<Snapshot> findByIdAndUserId(long tagId, long userId) {
    return Optional.ofNullable(mapper.findByIdAndUserId(tagId, userId)).map(this::toSnapshot);
  }

  @Override
  public Optional<Snapshot> findActiveByKey(
      long userId, ProfileTagType type, String value, ProfileTagSource source) {
    return Optional.ofNullable(mapper.findActiveByKey(userId, type.name(), value, source.name()))
        .map(this::toSnapshot);
  }

  @Override
  public boolean updateBehaviorWeight(
      long tagId,
      long expectedVersion,
      BigDecimal weight,
      ProfileTagPolarity polarity,
      LocalDateTime expiresAt,
      LocalDateTime updatedAt) {
    return mapper.updateBehaviorWeight(
            tagId, expectedVersion, weight, polarity.name(), expiresAt, updatedAt)
        == 1;
  }

  @Override
  public boolean updateStatus(
      long tagId,
      long userId,
      long expectedVersion,
      ProfileTagStatus status,
      LocalDateTime updatedAt) {
    return mapper.updateStatus(tagId, userId, expectedVersion, status.name(), updatedAt) == 1;
  }

  @Override
  public boolean update(
      long tagId,
      long userId,
      long expectedVersion,
      ProfileTagPolarity polarity,
      BigDecimal weight,
      BigDecimal confidence,
      LocalDateTime expiresAt,
      LocalDateTime updatedAt) {
    return mapper.update(
            tagId,
            userId,
            expectedVersion,
            polarity.name(),
            weight,
            confidence,
            expiresAt,
            updatedAt)
        == 1;
  }

  @Override
  public List<Snapshot> findPageByUserId(long userId, int offset, int limit) {
    return mapper.findPageByUserId(userId, offset, limit).stream().map(this::toSnapshot).toList();
  }

  @Override
  public long countByUserId(long userId) {
    return mapper.countByUserId(userId);
  }

  @Override
  public boolean softDelete(
      long tagId, long userId, long expectedVersion, LocalDateTime deletedAt) {
    return mapper.softDelete(tagId, userId, expectedVersion, deletedAt) == 1;
  }

  @Override
  public int softDeleteAll(long userId, LocalDateTime deletedAt) {
    return mapper.softDeleteAll(userId, deletedAt);
  }

  @Override
  public int cleanupDeletedBefore(LocalDateTime before, int limit) {
    return mapper.cleanupDeletedBefore(before, limit);
  }

  @Override
  public boolean updateBehaviorState(
      long tagId,
      long expectedVersion,
      BigDecimal weight,
      ProfileTagStatus status,
      LocalDateTime expiresAt,
      LocalDateTime updatedAt) {
    return mapper.updateBehaviorState(
            tagId, expectedVersion, weight, status.name(), expiresAt, updatedAt)
        == 1;
  }

  @Override
  public List<Snapshot> findBehaviorTagsDueBefore(LocalDateTime before, int limit) {
    return mapper.findBehaviorTagsDueBefore(before, limit).stream().map(this::toSnapshot).toList();
  }

  private Snapshot toSnapshot(ProfileTagRow row) {
    return new Snapshot(
        row.id(),
        row.userId(),
        ProfileTagType.valueOf(row.tagType()),
        row.tagValue(),
        ProfileTagPolarity.valueOf(row.polarity()),
        row.weight(),
        ProfileTagSource.valueOf(row.source()),
        row.confidence(),
        ProfileTagStatus.valueOf(row.status()),
        row.expiresAt(),
        row.version(),
        row.updatedAt());
  }
}
