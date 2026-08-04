package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import com.miaoyu.ticket.content.application.ContentPersistencePort.SyncLogRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ContentSyncServiceTest {
    @Test
    void givenQualifiedLiveResult_whenSynchronize_thenItOverwritesBothSnapshotAndCache() {
        ContentQuery query = new ContentQuery(ContentResourceType.MOVIE, 1L, null, null);
        ContentResult<List<? extends ContentItem>> result = new ContentResult<>(List.of(
                new MovieContent("1", "测试片", "剧情", 90, new BigDecimal("8.0"))),
                new ContentSource("NETSTART_MAOYAN", ContentSourceType.LIVE), LocalDateTime.of(2026, 8, 4, 9, 0),
                LocalDateTime.of(2026, 8, 4, 15, 0), false, false, null);
        AtomicInteger snapshots = new AtomicInteger();
        AtomicInteger caches = new AtomicInteger();
        AtomicInteger logs = new AtomicInteger();
        ContentSyncService service = new ContentSyncService(
                () -> batch(List.of(new LiveContentSyncPort.SynchronizedContent(query, result)), 1,
                        LiveContentSyncPort.Outcome.SUCCESS, null), snapshotPort(snapshots), cachePort(caches),
                persistence(logs, new AtomicReference<>()), () -> 99L,
                Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Shanghai")));
        // 同步写入两个层级，防止旧缓存压过刚保存的真实快照。
        assertThat(service.synchronizeDailyContent()).isEqualTo(1);
        assertThat(snapshots).hasValue(1);
        assertThat(caches).hasValue(1);
        assertThat(logs).hasValue(1);
    }

    @Test
    void givenDemoResultFromBrokenSyncPort_whenSynchronize_thenItDoesNotPolluteLiveLayers() {
        ContentQuery query = new ContentQuery(ContentResourceType.MOVIE, 1L, null, null);
        ContentResult<List<? extends ContentItem>> demo = new ContentResult<>(List.of(
                new MovieContent("demo-1", "演示片", "剧情", 90, new BigDecimal("8.0"))),
                new ContentSource("DEMO_CONTENT", ContentSourceType.MOCK), LocalDateTime.of(2026, 8, 4, 9, 0),
                LocalDateTime.of(2026, 8, 4, 15, 0), false, true,
                com.miaoyu.ticket.content.domain.ContentFallbackType.MOCK);
        AtomicInteger snapshots = new AtomicInteger();
        AtomicInteger caches = new AtomicInteger();
        ContentSyncService service = new ContentSyncService(
                () -> batch(List.of(new LiveContentSyncPort.SynchronizedContent(query, demo)), 1,
                        LiveContentSyncPort.Outcome.FIELD_REJECTED, null), snapshotPort(snapshots), cachePort(caches),
                persistence(new AtomicInteger(), new AtomicReference<>()), () -> 99L,
                Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Shanghai")));

        // 即使端口实现错误，3.1 也不能让 Demo 通过同步写入真实快照或缓存。
        assertThat(service.synchronizeDailyContent()).isZero();
        assertThat(snapshots).hasValue(0);
        assertThat(caches).hasValue(0);
    }

    @Test
    void givenLiveProviderContent_whenSynchronize_thenSnapshotsAndCacheUsePersistedBusinessIds() {
        ContentResult<List<? extends ContentItem>> movieResult = liveResult(List.of(
                new MovieContent("netstart-movie-1", "测试影片", "[\"剧情\"]", 90, new BigDecimal("8.0"))));
        ContentResult<List<? extends ContentItem>> cinemaResult = liveResult(List.of(
                new CinemaContent("netstart-cinema-1", "测试影院", "70", "天心区", "长沙市天心区测试路",
                        new BigDecimal("112.9"), new BigDecimal("28.1"))));
        List<ContentResult<List<? extends ContentItem>>> savedSnapshots = new ArrayList<>();
        List<ContentResult<List<? extends ContentItem>>> savedCaches = new ArrayList<>();
        AtomicReference<ContentPersistencePort.MovieRow> savedMovie = new AtomicReference<>();
        AtomicReference<ContentPersistencePort.CinemaRow> savedCinema = new AtomicReference<>();
        ContentSyncService service = new ContentSyncService(
                () -> batch(List.of(
                        new LiveContentSyncPort.SynchronizedContent(
                                new ContentQuery(ContentResourceType.MOVIE, null, null, null), movieResult),
                        new LiveContentSyncPort.SynchronizedContent(
                                new ContentQuery(ContentResourceType.CINEMA, null, "70", "影"), cinemaResult)),
                        2, LiveContentSyncPort.Outcome.SUCCESS, null),
                snapshotPort(savedSnapshots), cachePort(savedCaches),
                persistenceWithBusinessIds(savedMovie, savedCinema), () -> 9_999L,
                Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Shanghai")));

        assertThat(service.synchronizeDailyContent()).isEqualTo(2);
        assertThat(savedMovie.get().sourceMovieId()).isEqualTo("netstart-movie-1");
        assertThat(savedCinema.get().sourceCinemaId()).isEqualTo("netstart-cinema-1");
        assertThat(savedSnapshots).hasSize(2);
        assertThat(savedCaches).hasSize(2);
        assertThat(MovieContent.class.cast(savedSnapshots.getFirst().data().getFirst()).movieId()).isEqualTo(2_001L);
        assertThat(CinemaContent.class.cast(savedSnapshots.get(1).data().getFirst()).cinemaId()).isEqualTo(2_002L);
        assertThat(MovieContent.class.cast(savedCaches.getFirst().data().getFirst()).movieId()).isEqualTo(2_001L);
        assertThat(CinemaContent.class.cast(savedCaches.get(1).data().getFirst()).cinemaId()).isEqualTo(2_002L);
    }

    @Test
    void givenRateLimitedBatch_whenSynchronize_thenItWritesOnlySafeAuditSummary() {
        AtomicReference<SyncLogRow> captured = new AtomicReference<>();
        ContentSyncService service = new ContentSyncService(
                () -> batch(List.of(), 1, LiveContentSyncPort.Outcome.RATE_LIMITED, 429),
                snapshotPort(new AtomicInteger()), cachePort(new AtomicInteger()),
                persistence(new AtomicInteger(), captured), () -> 99L,
                Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Shanghai")));

        assertThat(service.synchronizeDailyContent()).isZero();
        assertThat(captured.get().status()).isEqualTo(ContentPersistencePort.SyncStatus.FAILED);
        assertThat(captured.get().errorCode()).isEqualTo(429);
        assertThat(captured.get().errorSummary()).contains("outcome=RATE_LIMITED", "elapsedMs=0",
                "dataTime=NONE", "quality=accepted:0,rejected:1", "fallback=NONE");
    }

    @Test
    void givenFailureCategories_whenSynchronize_thenEachCategoryIsRecordedWithoutProviderPayload() {
        assertFailureAudit(LiveContentSyncPort.Outcome.CONNECTION_FAILED, null);
        assertFailureAudit(LiveContentSyncPort.Outcome.UPSTREAM_FAILED, 502);
        assertFailureAudit(LiveContentSyncPort.Outcome.FIELD_REJECTED, null);
    }
    private ContentSnapshotPort snapshotPort(AtomicInteger saved) { return new ContentSnapshotPort() {
        @Override public Optional<ContentResult<List<? extends ContentItem>>> findLatest(ContentQuery query) {
            return Optional.empty();
        }
        @Override public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) {
            saved.incrementAndGet();
        }
    }; }
    private ContentSnapshotPort snapshotPort(List<ContentResult<List<? extends ContentItem>>> saved) {
        return new ContentSnapshotPort() {
            @Override public Optional<ContentResult<List<? extends ContentItem>>> findLatest(ContentQuery query) {
                return Optional.empty();
            }

            @Override public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) {
                saved.add(result);
            }
        };
    }
    private ContentCachePort cachePort(AtomicInteger saved) { return new ContentCachePort() {
        @Override public Optional<ContentResult<List<? extends ContentItem>>> find(ContentQuery query) {
            return Optional.empty();
        }
        @Override public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) {
            saved.incrementAndGet();
        }
    }; }
    private ContentCachePort cachePort(List<ContentResult<List<? extends ContentItem>>> saved) {
        return new ContentCachePort() {
            @Override public Optional<ContentResult<List<? extends ContentItem>>> find(ContentQuery query) {
                return Optional.empty();
            }

            @Override public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) {
                saved.add(result);
            }
        };
    }
    private LiveContentSyncPort.DailySyncBatch batch(List<LiveContentSyncPort.SynchronizedContent> contents,
                                                      int attemptedCount, LiveContentSyncPort.Outcome outcome,
                                                      Integer errorCode) {
        return new LiveContentSyncPort.DailySyncBatch(contents, attemptedCount, outcome, errorCode);
    }

    private ContentResult<List<? extends ContentItem>> liveResult(List<? extends ContentItem> items) {
        return new ContentResult<>(items, new ContentSource("NETSTART_MAOYAN", ContentSourceType.LIVE),
                LocalDateTime.of(2026, 8, 4, 9, 0), LocalDateTime.of(2026, 8, 4, 15, 0), false, false, null);
    }

    /** 同步日志只保存固定结果分类和 HTTP 状态码，异常正文不能进入审计摘要。 */
    private void assertFailureAudit(LiveContentSyncPort.Outcome outcome, Integer errorCode) {
        AtomicReference<SyncLogRow> captured = new AtomicReference<>();
        ContentSyncService service = new ContentSyncService(
                () -> batch(List.of(), 1, outcome, errorCode), snapshotPort(new AtomicInteger()),
                cachePort(new AtomicInteger()), persistence(new AtomicInteger(), captured), () -> 99L,
                Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Shanghai")));

        assertThat(service.synchronizeDailyContent()).isZero();
        assertThat(captured.get().status()).isEqualTo(ContentPersistencePort.SyncStatus.FAILED);
        assertThat(captured.get().errorCode()).isEqualTo(errorCode);
        assertThat(captured.get().errorSummary()).contains("outcome=" + outcome, "dataTime=NONE", "fallback=NONE")
                .doesNotContain("apiKey", "comment", "showInfo", "address");
    }

    private ContentPersistencePort persistence(AtomicInteger logs, AtomicReference<SyncLogRow> captured) {
        return new ContentPersistencePort() {
        @Override public long ensureMovie(MovieRow row) { return row.id(); }
        @Override public long ensureCinema(CinemaRow row) { return row.id(); }
        @Override public void insertSnapshot(SnapshotRow row) { }
        @Override public void insertSyncLog(SyncLogRow row) {
            logs.incrementAndGet();
            captured.set(row);
        }
    };
    }

    private ContentPersistencePort persistenceWithBusinessIds(
            AtomicReference<ContentPersistencePort.MovieRow> savedMovie,
            AtomicReference<ContentPersistencePort.CinemaRow> savedCinema) {
        return new ContentPersistencePort() {
            @Override public long ensureMovie(MovieRow row) {
                savedMovie.set(row);
                return 2_001L;
            }

            @Override public long ensureCinema(CinemaRow row) {
                savedCinema.set(row);
                return 2_002L;
            }

            @Override public void insertSnapshot(SnapshotRow row) { }
            @Override public void insertSyncLog(SyncLogRow row) { }
        };
    }
}
