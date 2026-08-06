package com.miaoyu.ticket.profile.application;

import java.util.Optional;

/** 摘要缓存端口；Redis 失败必须被调用方当作未命中而非画像不可用。 */
public interface ProfileSummaryCache {
  Optional<ProfileSummary> find(long userId, long version);

  void put(long userId, long version, ProfileSummary summary);

  void invalidateUser(long userId);
}
