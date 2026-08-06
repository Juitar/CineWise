package com.miaoyu.ticket.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 固定时钟验证衰减边界，避免定时任务在同一窗口重复扣减。 */
class BehaviorTagDecayTest {
  @Test
  void shouldDecayEveryThirtyDaysAndExpireAtFixedBehaviorDeadline() {
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    assertThat(
            BehaviorTagDecay.decay(
                new BigDecimal("0.800"), start, start.plusSeconds(60L * 60 * 24 * 30)))
        .isEqualByComparingTo("0.680");
    Instant expiresAt = start.plusSeconds(60L * 60 * 24 * 90);
    assertThat(BehaviorTagDecay.isExpiredAt(expiresAt, expiresAt.minusSeconds(1))).isFalse();
    assertThat(BehaviorTagDecay.isExpiredAt(expiresAt, expiresAt)).isTrue();
  }
}
