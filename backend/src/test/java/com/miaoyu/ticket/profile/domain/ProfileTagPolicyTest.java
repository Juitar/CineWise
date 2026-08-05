package com.miaoyu.ticket.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 验证关闭、过期和超限行为标签都不会进入推荐摘要。 */
class ProfileTagPolicyTest {
  @Test
  void shouldApplySummaryAndWeightRules() {
    Instant now = Instant.parse("2026-08-05T00:00:00Z");
    assertThat(ProfileTagPolicy.isAvailableForSummary(ProfileTagStatus.ACTIVE, null, now)).isTrue();
    assertThat(ProfileTagPolicy.isAvailableForSummary(ProfileTagStatus.DISABLED, null, now))
        .isFalse();
    assertThat(
            ProfileTagPolicy.isWeightValid(ProfileTagSource.MANUAL, new BigDecimal("0.09"), null))
        .isFalse();
    assertThat(
            ProfileTagPolicy.isWeightValid(ProfileTagSource.BEHAVIOR, new BigDecimal("0.80"), now))
        .isTrue();
  }
}
