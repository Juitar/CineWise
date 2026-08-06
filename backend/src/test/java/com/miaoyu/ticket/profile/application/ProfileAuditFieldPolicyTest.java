package com.miaoyu.ticket.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 验证画像审计字段白名单不会放行偏好正文和敏感原始数据。 */
class ProfileAuditFieldPolicyTest {
  @Test
  void shouldAllowOnlyMinimalAuditFields() {
    assertThat(ProfileAuditFieldPolicy.isAllowed("userId")).isTrue();
    assertThat(ProfileAuditFieldPolicy.isAllowed("traceId")).isTrue();
    assertThat(ProfileAuditFieldPolicy.isAllowed("tagValue")).isFalse();
    assertThat(ProfileAuditFieldPolicy.isAllowed("payload")).isFalse();
    assertThat(ProfileAuditFieldPolicy.isAllowed("email")).isFalse();
    assertThat(ProfileAuditFieldPolicy.isAllowed("latitude")).isFalse();
  }
}
