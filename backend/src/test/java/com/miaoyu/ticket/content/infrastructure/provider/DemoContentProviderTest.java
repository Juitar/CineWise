package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.application.ContentProperties;
import com.miaoyu.ticket.content.application.ContentIdentityLookupPort;
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
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class DemoContentProviderTest {

    private final DemoContentCatalogProvider catalogProvider = new ClasspathDemoContentCatalogProvider(
            new com.fasterxml.jackson.databind.ObjectMapper(), new DefaultResourceLoader());
    private final ContentIdentityLookupPort identityLookupPort = (resourceType, contentId) -> {
        if (resourceType == ContentResourceType.MOVIE && contentId == 8_100_001L) {
            return java.util.Optional.of("mock-movie-01");
        }
        return java.util.Optional.empty();
    };
    private final DemoContentProvider provider = new DemoContentProvider(
            catalogProvider, new ContentProperties(Duration.ofHours(6), Duration.ofHours(6), Duration.ofDays(7)),
            identityLookupPort);

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
        assertThat(first.dataTime()).isEqualTo("2026-08-01T00:00:00");
        assertThat(first.expiresAt()).isEqualTo("2026-08-01T06:00:00");
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

        // 合法筛选无匹配仍表示目录可用；返回空列表而不是补造影片、场次或价格。
        ContentResult<List<? extends ContentItem>> result = provider.query(query).orElseThrow();
        assertThat(result.data()).isEmpty();
    }

    @Test
    void givenKnownDatabaseMovieId_whenQueryDemoContent_thenOnlyItsMappedEntryCarriesThatId() {
        ContentQuery query = new ContentQuery(ContentResourceType.MOVIE, 8_100_001L, null, null);

        ContentResult<List<? extends ContentItem>> result = provider.query(query).orElseThrow();

        // 缓存、快照未命中后的 Demo 回退也必须保持调用方指定的实际数据库主键。
        assertThat(result.data()).hasSize(1);
        MovieContent movie = (MovieContent) result.data().getFirst();
        assertThat(movie.movieId()).isEqualTo(8_100_001L);
        assertThat(movie.sourceMovieId()).isEqualTo("mock-movie-01");
    }

    @Test
    void givenDemoMovieWithDisplayMetadata_whenBindingDatabaseId_thenItKeepsAllDisplayFields() {
        MovieContent catalogMovie = new MovieContent(null, "demo-metadata", "演示资料片", "[\"剧情\"]", 100,
                new java.math.BigDecimal("8.8"), "https://example.test/poster.jpg", "固定演示简介",
                "2026-08-01", "NOW_SHOWING");
        DemoContentCatalogProvider metadataCatalog = () -> new DemoContentCatalog("demo-content-v1",
                "2026-08-01T00:00:00", "DEMO_CONTENT", ContentSourceType.MOCK, List.of(catalogMovie), List.of());
        ContentIdentityLookupPort lookup = (resourceType, contentId) -> java.util.Optional.of("demo-metadata");
        DemoContentProvider metadataProvider = new DemoContentProvider(metadataCatalog,
                new ContentProperties(Duration.ofHours(6), Duration.ofHours(6), Duration.ofDays(7)), lookup);

        MovieContent result = (MovieContent) metadataProvider.query(
                new ContentQuery(ContentResourceType.MOVIE, 8_100_009L, null, null)).orElseThrow().data().getFirst();

        assertThat(result.movieId()).isEqualTo(8_100_009L);
        assertThat(result.posterUrl()).isEqualTo("https://example.test/poster.jpg");
        assertThat(result.summary()).isEqualTo("固定演示简介");
        assertThat(result.releaseDate()).isEqualTo("2026-08-01");
        assertThat(result.releaseStatus()).isEqualTo("NOW_SHOWING");
    }

    @Test
    void givenUnknownDatabaseMovieId_whenQueryDemoContent_thenItDoesNotReturnTheCatalogList() {
        ContentQuery query = new ContentQuery(ContentResourceType.MOVIE, 8_199_999L, null, null);

        assertThat(provider.query(query)).isEmpty();
    }
}
