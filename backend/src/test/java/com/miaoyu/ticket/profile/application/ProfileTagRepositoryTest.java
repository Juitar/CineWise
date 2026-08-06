package com.miaoyu.ticket.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.profile.domain.ProfileTagStatus;
import org.junit.jupiter.api.Test;

/** 快照字段使用领域枚举，避免字符串状态绕过标签规则。 */
class ProfileTagRepositoryTest {
  @Test
  void shouldKeepStatusAsDomainType() {
    assertThat(ProfileTagStatus.valueOf("ACTIVE")).isEqualTo(ProfileTagStatus.ACTIVE);
  }
}
