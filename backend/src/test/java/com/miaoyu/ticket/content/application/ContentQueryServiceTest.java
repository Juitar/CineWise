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
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContentQueryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-03T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 9, 0);
    private static final ContentQuery QUERY = new ContentQuery(ContentResourceType.MOVIE, null, null, "测试");

    @Test
    void givenValidCache_whenQuery_thenItWinsOverSnapshotAndDemo() {
        ContentResult<List<? extends ContentItem>> cache = result(NOW.plusHours(1), false, ContentFallbackType.CACHE);
        ContentQueryService service = service(Optional.of(cache), Optional.of(result(NOW.plusHours(1), false,
                ContentFallbackType.SNAPSHOT)), Optional.of(result(NOW.plusHours(1), false,
                ContentFallbackType.MOCK)));

        assertThat(service.query(QUERY).fallbackType()).isEqualTo(ContentFallbackType.CACHE);
    }

    @Test
    void givenAllowedExpiredSnapshot_whenQuery_thenItIsExplicitlyReadonlyAndDemoIsNotUsed() {
        ContentResult<List<? extends ContentItem>> snapshot = result(
                NOW.minusHours(1), false, ContentFallbackType.SNAPSHOT);
        ContentQueryService service = service(Optional.empty(), Optional.of(snapshot), Optional.empty());

        // 过期快照可供页面标注时间展示，但 3.6 要求其 expired=true，推荐不能把它当作可购事实。
        ContentResult<List<? extends ContentItem>> result = service.query(QUERY);
        assertThat(result.expired()).isTrue();
        assertThat(result.fallbackType()).isEqualTo(ContentFallbackType.SNAPSHOT);
    }

    @Test
    void givenNoCacheSnapshotOrDemo_whenQuery_thenItReturnsDataUnavailable303004() {
        ContentQueryService service = service(Optional.empty(), Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> service.query(QUERY)).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(303004);
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

    private ContentResult<List<? extends ContentItem>> result(LocalDateTime expiresAt, boolean expired,
                                                               ContentFallbackType fallbackType) {
        LocalDateTime dataTime = expiresAt.isBefore(NOW) ? expiresAt.minusHours(6) : NOW;
        return new ContentResult<>(List.of(new MovieContent("test-movie", "测试影片", "[\"剧情\"]", 100,
                new BigDecimal("8.0"))), new ContentSource("TEST", ContentSourceType.MOCK), dataTime, expiresAt,
                expired, true, fallbackType);
    }
}
