package com.miaoyu.ticket.profile.domain;

import java.math.BigDecimal;

/** 行为累计形成标签前的固定阈值，避免一次误触变成长期偏好。 */
public final class ProfileBehaviorRule {
  public static final BigDecimal TAG_THRESHOLD = new BigDecimal("0.300");
  public static final BigDecimal MAX_WEIGHT = new BigDecimal("0.800");

  private ProfileBehaviorRule() { }

  public static boolean shouldCreateOrUpdateTag(BigDecimal accumulatedWeight) {
    return accumulatedWeight != null && accumulatedWeight.abs().compareTo(TAG_THRESHOLD) >= 0;
  }

  public static BigDecimal capWeight(BigDecimal weight) {
    return weight.max(MAX_WEIGHT.negate()).min(MAX_WEIGHT);
  }
}
