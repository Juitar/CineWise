package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentItem;
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

    /** 同名影片和影院属于不同资源类型，不能因为展示名相同而把其中一条错误隔离。 */
    @Test
    void givenMovieAndCinemaWithSameName_whenDecide_thenTheyRemainIndependent() {
        List<ContentItem> items = List.of(
                new MovieContent("movie-100", "同名内容", "剧情", 90, new BigDecimal("8.0")),
                new CinemaContent("cinema-100", "同名内容", "330100", "西湖区", "测试路 1 号", null, null));

        ContentIdentityPolicy.Decision decision = new ContentIdentityPolicy().decide("NETSTART_MAOYAN", items);

        assertThat(decision.accepted()).hasSize(2);
        assertThat(decision.rejected()).isEmpty();
    }
}
