package com.miaoyu.ticket.recommendation.application;

import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagSource;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import java.util.Objects;

/**
 * 推荐实际采用画像时返回的最小证据。
 * 不复制完整 ProfileSummary，也不暴露原始行为、内部标签 ID 或会话内容。
 */
public record ProfileRecommendationEvidence(
    ProfileTagType type,
    String value,
    ProfileTagPolarity polarity,
    ProfileTagSource source) {
  public ProfileRecommendationEvidence {
    Objects.requireNonNull(type, "画像标签类型不能为空");
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("画像标签值不能为空");
    }
    Objects.requireNonNull(polarity, "画像标签极性不能为空");
    Objects.requireNonNull(source, "画像标签来源不能为空");
  }
}
