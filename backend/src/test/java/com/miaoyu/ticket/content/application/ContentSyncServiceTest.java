package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.MovieContent;
import com.miaoyu.ticket.content.application.ContentPersistencePort.SyncLogRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        // 单条影片同时写详情键和影片列表键；两者均在事务提交后覆盖对应旧缓存。
        assertThat(service.synchronizeDailyContent()).isEqualTo(1);
        assertThat(snapshots).hasValue(2);
        assertThat(caches).hasValue(2);
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

    @Test
    void givenDuplicateIdentityInSyncBatch_whenSynchronize_thenItPersistsOnlyAcceptedContentAndAuditsReason() {
        ContentQuery query = new ContentQuery(ContentResourceType.MOVIE, 1L, null, null);
        ContentResult<List<? extends ContentItem>> duplicate = new ContentResult<>(List.of(
                new MovieContent("same-id", "测试片甲", "剧情", 90, new BigDecimal("8.0")),
                new MovieContent("same-id", "测试片乙", "剧情", 90, new BigDecimal("8.1"))),
                new ContentSource("NETSTART_MAOYAN", ContentSourceType.LIVE), LocalDateTime.of(2026, 8, 4, 9, 0),
                LocalDateTime.of(2026, 8, 4, 15, 0), false, false, null);
        AtomicReference<ContentResult<List<? extends ContentItem>>> saved = new AtomicReference<>();
        AtomicReference<SyncLogRow> audit = new AtomicReference<>();
        ContentSyncService service = new ContentSyncService(
                () -> batch(List.of(new LiveContentSyncPort.SynchronizedContent(query, duplicate)), 2,
                        LiveContentSyncPort.Outcome.FIELD_REJECTED, null), captureSnapshot(saved),
                cachePort(new AtomicInteger()), persistence(new AtomicInteger(), audit), () -> 99L,
                Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Shanghai")));

        assertThat(service.synchronizeDailyContent()).isEqualTo(1);
        assertThat(saved.get().data()).hasSize(1);
        assertThat(((MovieContent) saved.get().data().getFirst()).movieId()).isEqualTo(99L);
        // 同步日志按内容项统计：两条候选中一条写入、一条因身份冲突拒绝，必须满足 V004 的 CHECK。
        assertThat(audit.get().totalCount()).isEqualTo(2);
        assertThat(audit.get().successCount()).isEqualTo(1);
        assertThat(audit.get().failureCount()).isEqualTo(1);
        assertThat(audit.get().status()).isEqualTo(ContentPersistencePort.SyncStatus.PARTIAL);
        assertThat(audit.get().errorSummary()).contains("identityRejected=1",
                "rejectionReason=IDENTITY_REVIEW_REQUIRED");
    }

    @Test
    void givenProviderQueriesUseExternalIdsAndSearchKeyword_whenSynchronize_thenSnapshotsUsePublicQueryKeys() {
        ContentQuery providerMovieQuery = new ContentQuery(ContentResourceType.MOVIE, 7001L, null, null);
        ContentQuery providerCinemaQuery = new ContentQuery(ContentResourceType.CINEMA, null, "70", "影");
        ContentResult<List<? extends ContentItem>> movieResult = new ContentResult<>(List.of(
                new MovieContent("7001", "真实影片", "[\"剧情\"]", 100, new BigDecimal("8.1"))),
                new ContentSource("NETSTART_MAOYAN", ContentSourceType.LIVE), LocalDateTime.of(2026, 8, 4, 9, 0),
                LocalDateTime.of(2026, 8, 4, 15, 0), false, false, null);
        ContentResult<List<? extends ContentItem>> cinemaResult = new ContentResult<>(List.of(
                new CinemaContent("8001", "真实影院", "70", "测试区", "测试地址", null, null)),
                new ContentSource("NETSTART_MAOYAN", ContentSourceType.LIVE), LocalDateTime.of(2026, 8, 4, 9, 0),
                LocalDateTime.of(2026, 8, 4, 15, 0), false, false, null);
        Map<ContentQuery, ContentResult<List<? extends ContentItem>>> snapshots = new LinkedHashMap<>();
        ContentSyncService service = new ContentSyncService(
                () -> batch(List.of(new LiveContentSyncPort.SynchronizedContent(providerMovieQuery, movieResult),
                        new LiveContentSyncPort.SynchronizedContent(providerCinemaQuery, cinemaResult)), 2,
                        LiveContentSyncPort.Outcome.SUCCESS, null), captureSnapshots(snapshots),
                cachePort(new AtomicInteger()), persistence(new AtomicInteger(), new AtomicReference<>()), () -> 99L,
                Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Shanghai")));

        assertThat(service.synchronizeDailyContent()).isEqualTo(2);
        // 外部 7001 和同步关键词“影”都不能进入公开缓存/快照键；页面只会用内部 ID 或列表条件查询。
        assertThat(snapshots).containsKeys(
                new ContentQuery(ContentResourceType.MOVIE, 99L, null, null),
                new ContentQuery(ContentResourceType.MOVIE, null, null, null),
                new ContentQuery(ContentResourceType.CINEMA, 99L, null, null),
                new ContentQuery(ContentResourceType.CINEMA, null, "70", null));
        assertThat(snapshots).doesNotContainKeys(providerMovieQuery, providerCinemaQuery);
    }

    @Test
    void givenTransactionRollsBack_whenSynchronize_thenItDoesNotPublishLiveCache() {
        ContentQuery query = new ContentQuery(ContentResourceType.MOVIE, 1L, null, null);
        ContentResult<List<? extends ContentItem>> result = new ContentResult<>(List.of(
                new MovieContent("1", "测试片", "[\"剧情\"]", 90, new BigDecimal("8.0"))),
                new ContentSource("NETSTART_MAOYAN", ContentSourceType.LIVE), LocalDateTime.of(2026, 8, 4, 9, 0),
                LocalDateTime.of(2026, 8, 4, 15, 0), false, false, null);
        AtomicInteger caches = new AtomicInteger();
        ContentSyncService service = new ContentSyncService(
                () -> batch(List.of(new LiveContentSyncPort.SynchronizedContent(query, result)), 1,
                        LiveContentSyncPort.Outcome.SUCCESS, null), snapshotPort(new AtomicInteger()),
                cachePort(caches),
                persistence(new AtomicInteger(), new AtomicReference<>()), () -> 99L,
                Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Shanghai")));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.synchronizeDailyContent();
            // 模拟事务回滚：不触发 afterCommit，Redis 不能得到无法由 MySQL 追溯的 LIVE 数据。
            assertThat(caches).hasValue(0);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
    private ContentSnapshotPort snapshotPort(AtomicInteger saved) { return new ContentSnapshotPort() {
        @Override public Optional<ContentResult<List<? extends ContentItem>>> findLatest(ContentQuery query) {
            return Optional.empty();
        }
        @Override public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) {
            saved.incrementAndGet();
        }
    }; }
    private ContentCachePort cachePort(AtomicInteger saved) { return new ContentCachePort() {
        @Override public Optional<ContentResult<List<? extends ContentItem>>> find(ContentQuery query) {
            return Optional.empty();
        }
        @Override public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) {
            saved.incrementAndGet();
        }
    }; }
    private ContentSnapshotPort captureSnapshot(AtomicReference<ContentResult<List<? extends ContentItem>>> saved) {
        return new ContentSnapshotPort() {
            @Override public Optional<ContentResult<List<? extends ContentItem>>> findLatest(ContentQuery query) {
                return Optional.empty();
            }
            @Override public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) {
                saved.set(result);
            }
        };
    }
    private ContentSnapshotPort captureSnapshots(Map<ContentQuery, ContentResult<List<? extends ContentItem>>> saved) {
        return new ContentSnapshotPort() {
            @Override public Optional<ContentResult<List<? extends ContentItem>>> findLatest(ContentQuery query) {
                return Optional.empty();
            }
            @Override public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) {
                saved.put(query, result);
            }
        };
    }
    private LiveContentSyncPort.DailySyncBatch batch(List<LiveContentSyncPort.SynchronizedContent> contents,
                                                      int attemptedCount, LiveContentSyncPort.Outcome outcome,
                                                      Integer errorCode) {
        return new LiveContentSyncPort.DailySyncBatch(contents, attemptedCount, outcome, errorCode);
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
}
