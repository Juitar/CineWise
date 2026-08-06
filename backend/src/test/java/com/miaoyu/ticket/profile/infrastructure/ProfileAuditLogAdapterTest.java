package com.miaoyu.ticket.profile.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 审计适配器必须在最终输出前去掉标签正文和原始行为内容。 */
class ProfileAuditLogAdapterTest {
  @Test
  void shouldFilterSensitiveFieldsBeforeLogging() {
    Map<String, String> filtered =
        ProfileAuditLogAdapter.filterFields(
            Map.of(
                "userId", "18",
                "operation", "UPDATE_TAG",
                "tagValue", "科幻",
                "payload", "{raw}",
                "email", "user@example.com"));

    assertThat(filtered).containsOnlyKeys("userId", "operation");
  }
}
