package com.miaoyu.ticket.profile.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** 画像标签的纯领域规则，不读取数据库或其他模块事实。 */
public final class ProfileTagPolicy {

  private static final BigDecimal MANUAL_MIN_WEIGHT = new BigDecimal("0.100");
  private static final BigDecimal MAX_WEIGHT = new BigDecimal("1.000");
  private static final BigDecimal BEHAVIOR_MAX_WEIGHT = new BigDecimal("0.800");

  private ProfileTagPolicy() { }

  /** 只有启用且未到期的标签才允许出现在最小画像摘要中。 */
  public static boolean isAvailableForSummary(
      ProfileTagStatus status, Instant expiresAt, Instant now) {
    return status == ProfileTagStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(now));
  }

  /** 手工偏好必须是用户明确给出的有效权重，行为偏好不能借此获得更高权重。 */
  public static boolean isWeightValid(
      ProfileTagSource source, BigDecimal weight, Instant expiresAt) {
    if (weight == null || weight.signum() < 0 || weight.compareTo(MAX_WEIGHT) > 0) {
      return false;
    }
    return switch (source) {
      case MANUAL -> weight.compareTo(MANUAL_MIN_WEIGHT) >= 0;
      case CONVERSATION -> true;
      case BEHAVIOR -> weight.compareTo(BEHAVIOR_MAX_WEIGHT) <= 0 && expiresAt != null;
    };
  }
}
