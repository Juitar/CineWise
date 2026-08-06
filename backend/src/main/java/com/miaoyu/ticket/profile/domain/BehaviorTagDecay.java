package com.miaoyu.ticket.profile.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/** 行为标签每 30 天衰减，90 天未更新则过期的固定规则。 */
public final class BehaviorTagDecay {
  private static final BigDecimal FACTOR = new BigDecimal("0.85");

  private BehaviorTagDecay() { }

  /** 只按完整经过的 30 天窗口衰减，定时任务重复执行不会重复扣减。 */
  public static BigDecimal decay(BigDecimal weight, Instant lastUpdatedAt, Instant now) {
    long periods = Duration.between(lastUpdatedAt, now).toDays() / 30;
    BigDecimal result = weight;
    for (long index = 0; index < periods; index++) {
      result = result.multiply(FACTOR);
    }
    return result.setScale(3, RoundingMode.HALF_UP);
  }

  /**
   * 行为标签的 expiresAt 由新行为创建或聚合时确定，衰减任务不能改它。
   * 因此任务更新 update_time 后，也不会把 90 天终态推迟。
   */
  public static boolean isExpiredAt(Instant expiresAt, Instant now) {
    return !expiresAt.isAfter(now);
  }
}
