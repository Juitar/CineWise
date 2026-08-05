package com.miaoyu.ticket.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 关闭个性化时摘要不得泄漏已有标签。 */
class ProfileSummaryTest {
  @Test
  void shouldHideTagsWhenDisabled() {
    assertThat(new ProfileSummary(false, 3, Instant.EPOCH, List.of()).tags()).isEmpty();
  }
}
