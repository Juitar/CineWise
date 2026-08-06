package com.miaoyu.ticket.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 同一规范化请求必须产生稳定摘要，改变参数必须产生不同摘要。 */
class ProfileRequestHasherTest {
  @Test
  void shouldProduceStableSha256ForNormalizedParameters() {
    String first = ProfileRequestHasher.sha256("MOVIE_GENRE|科幻|LIKE|0.900");
    assertThat(ProfileRequestHasher.sha256("MOVIE_GENRE|科幻|LIKE|0.900")).isEqualTo(first);
    assertThat(ProfileRequestHasher.sha256("MOVIE_GENRE|恐怖|DISLIKE|0.900")).isNotEqualTo(first);
    assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
  }
}
