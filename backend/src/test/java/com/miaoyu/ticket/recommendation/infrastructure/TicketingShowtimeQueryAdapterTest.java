package com.miaoyu.ticket.recommendation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.recommendation.application.RecommendationQuery;
import com.miaoyu.ticket.ticketing.application.ShowQuery;
import com.miaoyu.ticket.ticketing.application.ShowQueryService;
import com.miaoyu.ticket.ticketing.application.ShowSummaryView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TicketingShowtimeQueryAdapterTest {

    /*
     * 适配器测试只验证 A 的公开 Application DTO 到 D 端口的字段映射。
     * 不访问 Controller，保证模块化单体内部不走 HTTP。
     * 不读取 Mapper 或票务表，避免 D 跨模块访问持久化实现。
     * basePrice 必须被原样格式化为两位小数 price。
     * expiresAt 必须完全沿用 A 返回的候选时效。
     * 座位和库存不应出现在 D 的候选对象中。
     */

    @Test
    void shouldMapOnlyAProvidedShowFactsIncludingCandidateExpiry() {
        ShowQueryService showQueryService = mock(ShowQueryService.class);
        LocalDate date = LocalDate.of(2026, 8, 3);
        LocalDateTime startTime = LocalDateTime.of(2026, 8, 3, 19, 30);
        ShowSummaryView show = new ShowSummaryView(
                301L, 101L, 201L, "妙语影城", 401L, "1号厅", startTime, startTime.plusHours(2),
                startTime, "国语2D", new BigDecimal("45.00"), 80, "ON_SALE", "MOCK", 1, startTime.minusHours(1));
        when(showQueryService.queryShows(new ShowQuery(101L, 201L, date, null, null))).thenReturn(List.of(show));

        // Mock 的唯一作用是固定 A 的公开返回，不代表 D 自建了一份场次数据。
        var candidates = new TicketingShowtimeQueryAdapter(showQueryService)
                .querySaleable(new RecommendationQuery("101", "201", date, null, null));

        // D 只映射 A 返回的主键、basePrice、时间和来源，不增加库存、座位或自行生成场次。
        assertThat(candidates).singleElement().satisfies(candidate -> {
            // 场次 ID 由 A 保持原样提供。
            assertThat(candidate.showId()).isEqualTo("301");
            // 影片 ID 由 A 保持原样提供。
            assertThat(candidate.movieId()).isEqualTo("101");
            // 影院 ID 由 A 保持原样提供。
            assertThat(candidate.cinemaId()).isEqualTo("201");
            // 金额只做字符串映射。
            assertThat(candidate.price()).isEqualTo("45.00");
            // 时效值不由 D 重新计算。
            assertThat(candidate.expiresAt()).isEqualTo(candidate.startTime());
        });
    }
}
