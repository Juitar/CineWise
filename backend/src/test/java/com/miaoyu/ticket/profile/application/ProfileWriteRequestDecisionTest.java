package com.miaoyu.ticket.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 验证幂等键只重放完全相同的请求，避免把不同编辑误当成网络重试。 */
class ProfileWriteRequestDecisionTest {
  @Test
  void shouldReplayOnlySameRequestHash() {
    assertThat(ProfileWriteRequestDecision.decide(null, "a"))
        .isEqualTo(ProfileWriteRequestDecision.EXECUTE_NEW_REQUEST);
    assertThat(ProfileWriteRequestDecision.decide("same", "same"))
        .isEqualTo(ProfileWriteRequestDecision.REPLAY_FIRST_RESPONSE);
    assertThat(ProfileWriteRequestDecision.decide("first", "second"))
        .isEqualTo(ProfileWriteRequestDecision.REJECT_PARAMETER_MISMATCH);
  }
}
