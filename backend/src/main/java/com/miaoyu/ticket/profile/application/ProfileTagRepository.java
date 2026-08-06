package com.miaoyu.ticket.profile.application;

import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagSource;
import com.miaoyu.ticket.profile.domain.ProfileTagStatus;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 标签持久化端口；应用服务只能通过它查询本人标签，不能直接依赖 Mapper。 */
public interface ProfileTagRepository {
  /** 新标签写入由已完成同意校验和归属校验的应用服务调用。 */
  void insert(NewTag tag);

  Optional<Snapshot> findByIdAndUserId(long tagId, long userId);

  boolean updateStatus(
      long tagId,
      long userId,
      long expectedVersion,
      ProfileTagStatus status,
      LocalDateTime updatedAt);

  List<Snapshot> findPageByUserId(long userId, int offset, int limit);

  boolean softDelete(long tagId, long userId, long expectedVersion, LocalDateTime deletedAt);

  /**
   * 行为标签由定时任务按旧版本更新，防止用户刚产生新行为时被旧批次覆盖。
   * 任务只能改行为来源标签，手工和对话标签不参加衰减。
   */
  boolean updateBehaviorState(
      long tagId,
      long expectedVersion,
      BigDecimal weight,
      ProfileTagStatus status,
      LocalDateTime expiresAt,
      LocalDateTime updatedAt);

  List<Snapshot> findBehaviorTagsDueBefore(LocalDateTime before, int limit);

  /** 最小标签快照不携带行为原始数据，供本人管理和摘要筛选复用。 */
  record Snapshot(
      long id,
      long userId,
      ProfileTagType type,
      String value,
      ProfileTagPolarity polarity,
      BigDecimal weight,
      ProfileTagSource source,
      BigDecimal confidence,
      ProfileTagStatus status,
      LocalDateTime expiresAt,
      long version,
      LocalDateTime updatedAt) { }

  /**
   * 写入参数不包含任意原始行为 payload，只保留摘要需要的最小标签字段。
   * 行为标签必须由上游传入明确有效期，数据库 CHECK 会再次约束。
   */
  record NewTag(
      long id,
      long userId,
      ProfileTagType type,
      String value,
      ProfileTagPolarity polarity,
      BigDecimal weight,
      ProfileTagSource source,
      BigDecimal confidence,
      ProfileTagStatus status,
      LocalDateTime expiresAt,
      LocalDateTime createdAt) { }
}
