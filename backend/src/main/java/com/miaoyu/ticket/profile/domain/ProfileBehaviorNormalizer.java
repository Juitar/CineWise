package com.miaoyu.ticket.profile.domain;

import java.math.BigDecimal;

/** 行为事件到固定偏好权重的映射，避免一次误触被解释为强偏好。 */
public final class ProfileBehaviorNormalizer {
  private ProfileBehaviorNormalizer() { }

  public static BigDecimal weightOf(ProfileBehaviorEventType type) {
    return switch (type) {
      case FAVORITE -> new BigDecimal("0.30");
      case ACCEPT_PLAN -> new BigDecimal("0.20");
      case PAID_ORDER -> new BigDecimal("0.35");
      case CLICK -> new BigDecimal("0.05");
      case REJECT_PLAN -> new BigDecimal("-0.15");
      case NOT_INTERESTED -> new BigDecimal("-0.30");
    };
  }
}
