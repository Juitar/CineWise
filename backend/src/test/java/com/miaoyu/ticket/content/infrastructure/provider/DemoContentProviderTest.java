package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.application.ContentProperties;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.application.DemoContentCatalog;
import com.miaoyu.ticket.content.application.DemoContentCatalogProvider;
import com.miaoyu.ticket.content.domain.ContentFallbackType;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import com.miaoyu.ticket.content.domain.CinemaContent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class DemoContentProviderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-03T01:02:03Z"), ZoneId.of("Asia/Shanghai"));

    private final DemoContentCatalogProvider catalogProvider = new ClasspathDemoContentCatalogProvider(
            new com.fasterxml.jackson.databind.ObjectMapper(), new DefaultResourceLoader());
    private final DemoContentProvider provider = new DemoContentProvider(
            catalogProvider, new ContentProperties(Duration.ofHours(6), Duration.ofHours(6), Duration.ofDays(7)),
            FIXED_CLOCK);

    @Test
    void givenSameCatalogQueryAndClock_whenQueryMoviesTwice_thenContentOrderAndSourceEnvelopeStayStable() {
        ContentQuery query = new ContentQuery(ContentResourceType.MOVIE, null, null, "星河");

        ContentResult<List<? extends ContentItem>> first = provider.query(query).orElseThrow();
        ContentResult<List<? extends ContentItem>> second = provider.query(query).orElseThrow();

        // 固定目录和固定时钟是演示与回归测试共用基线，重复查询不能出现随机顺序或当前时间差异。
        assertThat(second).isEqualTo(first);
        assertThat(first.data()).extracting(item -> ((MovieContent) item).sourceMovieId())
                .containsExactly("mock-movie-01");
        assertThat(first.source().name()).isEqualTo("DEMO_CONTENT");
        assertThat(first.source().type()).isEqualTo(ContentSourceType.MOCK);
        assertThat(first.dataTime()).isEqualTo("2026-08-03T09:02:03");
        assertThat(first.expiresAt()).isEqualTo("2026-08-03T15:02:03");
        assertThat(first.expired()).isFalse();
        assertThat(first.degraded()).isTrue();
        assertThat(first.fallbackType()).isEqualTo(ContentFallbackType.MOCK);
    }

    @Test
    void givenCityAndKeyword_whenQueryCinemas_thenOnlyMatchingCatalogEntriesKeepTheirCatalogOrder() {
        ContentQuery query = new ContentQuery(ContentResourceType.CINEMA, null, "330100", "影城");

        ContentResult<List<? extends ContentItem>> result = provider.query(query).orElseThrow();

        assertThat(result.data()).extracting(item -> ((CinemaContent) item).sourceCinemaId())
                .containsExactly("mock-cinema-01", "mock-cinema-02", "mock-cinema-03", "mock-cinema-04");
    }

    @Test
    void givenNoCatalogMatch_whenQueryDemoContent_thenItDoesNotInventContentOrTicketingFacts() {
        ContentQuery query = new ContentQuery(ContentResourceType.MOVIE, null, null, "不存在的影片");

        // 空结果交给后续 3.5 的回退顺序处理，Provider 不补造影片、场次或价格。
        assertThat(provider.query(query)).isEmpty();
    }
}
