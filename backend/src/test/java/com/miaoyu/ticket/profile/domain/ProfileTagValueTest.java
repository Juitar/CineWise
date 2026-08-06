package com.miaoyu.ticket.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 验证标签值规范化不会让空白或超长文本进入后续画像唯一键。 */
class ProfileTagValueTest {

  @Test
  void shouldTrimValidTagValue() {
    ProfileTagValue tagValue = new ProfileTagValue("  科幻  ");

    assertThat(tagValue.value()).isEqualTo("科幻");
  }

  @Test
  void shouldRejectBlankOrOversizedTagValue() {
    assertThatThrownBy(() -> new ProfileTagValue("  "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProfileTagValue("a".repeat(ProfileTagValue.MAX_LENGTH + 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
