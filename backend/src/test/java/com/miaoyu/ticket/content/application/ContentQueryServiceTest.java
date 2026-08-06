package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.domain.ContentFallbackType;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ContentQueryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-03T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 9, 0);
    private static final ContentQuery QUERY = new ContentQuery(ContentResourceType.MOVIE, null, null, "测试");

    @Test
    void givenValidCache_whenQuery_thenItWinsOverSnapshotAndDemo() {
        ContentResult<List<? extends ContentItem>> cache = result(NOW.plusHours(1), false, null,
                ContentSourceType.LIVE);
        ContentQueryService service = service(Optional.of(cache), Optional.of(result(NOW.plusHours(1), false,
                ContentFallbackType.SNAPSHOT, ContentSourceType.LIVE)), Optional.of(result(NOW.plusHours(1), false,
                ContentFallbackType.MOCK)));

        ContentResult<List<? extends ContentItem>> result = service.query(QUERY);
        assertThat(result.source().type()).isEqualTo(ContentSourceType.LIVE);
        assertThat(result.degraded()).isFalse();
        assertThat(result.fallbackType()).isNull();
    }

    @Test
    void givenLegacyDemoCache_whenQuery_thenItIsRejectedAndDemoRemainsLastFallback() {
        ContentQueryService service = service(Optional.of(result(NOW.plusHours(1), false,
                ContentFallbackType.CACHE)), Optional.empty(), Optional.of(result(NOW.plusHours(1), false,
                ContentFallbackType.MOCK)));

        ContentResult<List<? extends ContentItem>> result = service.query(QUERY);

        // 3.2 要求 Demo 不得进入缓存；兼容清理前遗留的 MOCK 缓存也不能被页面误报为真实缓存。
        assertThat(result.fallbackType()).isEqualTo(ContentFallbackType.MOCK);
    }

    @Test
    void givenLegacyDemoSnapshot_whenQuery_thenItIsRejectedAndDemoRemainsLastFallback() {
        ContentQueryService service = service(Optional.empty(), Optional.of(result(NOW.plusHours(1), false,
                ContentFallbackType.SNAPSHOT)), Optional.of(result(NOW.plusHours(1), false,
                ContentFallbackType.MOCK)));

        ContentResult<List<? extends ContentItem>> result = service.query(QUERY);

        // 快照层只允许同步得到的 LIVE 内容，不能把旧 Demo 快照提升成“历史真实资料”。
        assertThat(result.fallbackType()).isEqualTo(ContentFallbackType.MOCK);
    }

    @Test
    void givenExpiredCurrentSnapshot_whenQuery_thenItKeepsLiveDataAndOnlyMarksExpiration() {
        ContentResult<List<? extends ContentItem>> snapshot = result(
                NOW.minusHours(1), false, ContentFallbackType.SNAPSHOT, ContentSourceType.LIVE);
        ContentQueryService service = service(Optional.empty(), Optional.of(snapshot), Optional.empty());

        // 单版本存储下它仍是当前真实版本；isExpired 只提示时效，不能把它伪装为上一版本 SNAPSHOT。
        ContentResult<List<? extends ContentItem>> result = service.query(QUERY);
        assertThat(result.expired()).isTrue();
        assertThat(result.degraded()).isFalse();
        assertThat(result.fallbackType()).isNull();
    }

    @Test
    void givenCacheMissAndCurrentSnapshot_whenQuery_thenItReturnsTheLatestRealVersionWithoutDegradation() {
        ContentQueryService service = service(Optional.empty(), Optional.of(result(NOW.plusHours(1), false,
                ContentFallbackType.SNAPSHOT, ContentSourceType.LIVE)), Optional.empty());

        ContentResult<List<? extends ContentItem>> result = service.query(QUERY);

        // Redis 未命中不等于资料降级；当前完整真实快照仍是最新版本。
        assertThat(result.fallbackType()).isNull();
        assertThat(result.degraded()).isFalse();
        assertThat(result.expired()).isFalse();
    }

    @Test
    void givenVeryOldCurrentSnapshot_whenQuery_thenItStillWinsOverDemo() {
        ContentQueryService service = service(Optional.empty(), Optional.of(result(NOW.minusDays(8), false,
                ContentFallbackType.SNAPSHOT, ContentSourceType.LIVE)), Optional.of(result(NOW.plusHours(1), false,
                ContentFallbackType.MOCK)));

        ContentResult<List<? extends ContentItem>> result = service.query(QUERY);

        // 真实资料无论多旧都比 Demo 更有意义；两版本功能完成前不能用最大陈旧期强制丢弃它。
        assertThat(result.source().type()).isEqualTo(ContentSourceType.LIVE);
        assertThat(result.expired()).isTrue();
        assertThat(result.degraded()).isFalse();
        assertThat(result.fallbackType()).isNull();
    }

    @Test
    void givenDemoFallback_whenQuery_thenItMustNotBeSavedAsCache() {
        AtomicInteger cacheSaveCount = new AtomicInteger();
        ContentCachePort cache = new ContentCachePort() {
            @Override
            public Optional<ContentResult<List<? extends ContentItem>>> find(ContentQuery query) {
                return Optional.empty();
            }

            @Override
            public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) {
                cacheSaveCount.incrementAndGet();
            }
        };
        ContentSnapshotPort snapshot = new ContentSnapshotPort() {
            @Override
            public Optional<ContentResult<List<? extends ContentItem>>> findLatest(ContentQuery query) {
                return Optional.empty();
            }

            @Override
            public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) { }
        };
        ContentProvider demo = query -> Optional.of(result(NOW.plusHours(1), false, ContentFallbackType.MOCK));
        ContentQueryService service = new ContentQueryService(cache, snapshot, demo,
                new ContentProperties(Duration.ofHours(6), Duration.ofHours(6), Duration.ofDays(7)), CLOCK);

        ContentResult<List<? extends ContentItem>> result = service.query(QUERY);

        // Demo 是离线回退，不得因缓存而在后续响应中被错误标记为 CACHE。
        assertThat(result.fallbackType()).isEqualTo(ContentFallbackType.MOCK);
        assertThat(cacheSaveCount).hasValue(0);
    }

    @Test
    void givenNoCacheSnapshotOrDemo_whenQuery_thenItReturnsDataUnavailable303004() {
        ContentQueryService service = service(Optional.empty(), Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> service.query(QUERY)).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(303004);
    }

    @Test
    void givenSomeMovieIdsMissing_whenFindingSummaries_thenItReturnsAvailableItemsFromOneCatalogRead() {
        AtomicInteger cacheFindCount = new AtomicInteger();
        ContentResult<List<? extends ContentItem>> catalog = new ContentResult<>(List.of(
                new MovieContent(101L, "movie-101", "影片甲", "[\"剧情\"]", 100, new BigDecimal("8.0")),
                new MovieContent(102L, "movie-102", "影片乙", "[\"动作\"]", 110, new BigDecimal("8.5"))),
                new ContentSource("TEST", ContentSourceType.LIVE), NOW, NOW.plusHours(1), false, false, null);
        ContentCachePort cache = new ContentCachePort() {
            @Override
            public Optional<ContentResult<List<? extends ContentItem>>> find(ContentQuery query) {
                cacheFindCount.incrementAndGet();
                assertThat(query.resourceType()).isEqualTo(ContentResourceType.MOVIE);
                assertThat(query.contentId()).isNull();
                return Optional.of(catalog);
            }

            @Override
            public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) { }
        };
        ContentQueryService service = new ContentQueryService(cache, emptySnapshot(), query -> Optional.empty(),
                new ContentProperties(Duration.ofHours(6), Duration.ofHours(6), Duration.ofDays(7)), CLOCK);

        Map<Long, ContentPurchaseQueryPort.MovieSummary> summaries =
                service.findMovieSummaries(Set.of(102L, 101L, 999L));

        assertThat(cacheFindCount).hasValue(1);
        assertThat(summaries).containsOnlyKeys(101L, 102L);
        assertThat(summaries.get(101L).title()).isEqualTo("影片甲");
        assertThat(summaries.get(101L).contentSource()).isEqualTo("TEST");
        assertThat(summaries.get(101L).contentDataTime()).isEqualTo(NOW);
    }

    @Test
    void givenContentCatalogUnavailable_whenFindingSummaries_thenItKeepsDataUnavailable303004() {
        ContentQueryService service = service(Optional.empty(), Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> service.findMovieSummaries(Set.of(101L)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(303004);
    }

    @Test
    void givenMissingOrInvalidLocator_whenCreatingQuery_thenItRejectsBeforeAnyProviderAccess() {
        // 无定位条件和非正业务 ID 都必须在 Application 边界失败，避免生成无意义的缓存或快照键。
        assertThatThrownBy(() -> new ContentQuery(ContentResourceType.CINEMA, null, " ", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContentQuery(ContentResourceType.CINEMA, 0L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ContentQueryService service(Optional<ContentResult<List<? extends ContentItem>>> cacheResult,
                                        Optional<ContentResult<List<? extends ContentItem>>> snapshotResult,
                                        Optional<ContentResult<List<? extends ContentItem>>> demoResult) {
        ContentCachePort cache = new ContentCachePort() {
            @Override
            public Optional<ContentResult<List<? extends ContentItem>>> find(ContentQuery query) {
                return cacheResult;
            }
            @Override public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) { }
        };
        ContentSnapshotPort snapshot = new ContentSnapshotPort() {
            @Override
            public Optional<ContentResult<List<? extends ContentItem>>> findLatest(ContentQuery query) {
                return snapshotResult;
            }
            @Override public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) { }
        };
        ContentProvider demo = query -> demoResult;
        return new ContentQueryService(cache, snapshot, demo,
                new ContentProperties(Duration.ofHours(6), Duration.ofHours(6), Duration.ofDays(7)), CLOCK);
    }

    private ContentSnapshotPort emptySnapshot() {
        return new ContentSnapshotPort() {
            @Override
            public Optional<ContentResult<List<? extends ContentItem>>> findLatest(ContentQuery query) {
                return Optional.empty();
            }

            @Override
            public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) { }
        };
    }

    private ContentResult<List<? extends ContentItem>> result(LocalDateTime expiresAt, boolean expired,
                                                               ContentFallbackType fallbackType) {
        return result(expiresAt, expired, fallbackType, ContentSourceType.MOCK);
    }

    private ContentResult<List<? extends ContentItem>> result(LocalDateTime expiresAt, boolean expired,
                                                               ContentFallbackType fallbackType,
                                                               ContentSourceType sourceType) {
        LocalDateTime dataTime = expiresAt.isBefore(NOW) ? expiresAt.minusHours(6) : NOW;
        return new ContentResult<>(List.of(new MovieContent("test-movie", "测试影片", "[\"剧情\"]", 100,
                new BigDecimal("8.0"))), new ContentSource("TEST", sourceType), dataTime, expiresAt,
                expired, fallbackType != null, fallbackType);
    }
}
