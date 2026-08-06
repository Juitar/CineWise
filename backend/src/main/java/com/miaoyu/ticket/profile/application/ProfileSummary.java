package com.miaoyu.ticket.profile.application;

import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagSource;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 给推荐和 Agent 的最小画像摘要，不包含行为流水、邮箱或内部标签 ID。 */
public record ProfileSummary(boolean enabled, long version, Instant generatedAt, List<Tag> tags) {
  public ProfileSummary {
    tags = enabled ? List.copyOf(tags) : List.of();
  }

  /** 摘要只保留可解释的偏好字段，调用方不能据此反查持久化记录。 */
  public record Tag(
      ProfileTagType type,
      String value,
      ProfileTagPolarity polarity,
      BigDecimal weight,
      BigDecimal confidence,
      ProfileTagSource source,
      Instant updatedAt) { }
}
