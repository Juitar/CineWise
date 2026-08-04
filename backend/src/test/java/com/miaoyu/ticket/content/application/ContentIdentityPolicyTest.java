package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentIdentityPolicyTest {
    @Test
    void givenDuplicateIdentityAndSameNameDifferentId_whenDecide_thenOnlyStableIdentityIsAccepted() {
        ContentIdentityPolicy.Decision decision = new ContentIdentityPolicy().decide("NETSTART_MAOYAN", List.of(
                new MovieContent("100", "重名片", "剧情", 90, new BigDecimal("8.0")),
                new MovieContent("100", "重名片", "剧情", 90, new BigDecimal("8.0")),
                new MovieContent("101", "重名片", "剧情", 90, new BigDecimal("8.0"))));
        // 两条拒绝项都必须进入后续同步审计，不能按名称合并或覆盖已有内容。
        assertThat(decision.accepted()).hasSize(1);
        assertThat(decision.rejected()).hasSize(2).allMatch(item -> item.reason().equals("IDENTITY_REVIEW_REQUIRED"));
    }
}
