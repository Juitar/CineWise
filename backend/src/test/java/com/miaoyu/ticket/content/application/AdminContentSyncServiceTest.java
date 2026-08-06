package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.domain.ContentItem;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class AdminContentSyncServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-06T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void givenNewChangshaRequest_whenSyncCompletes_thenItCreatesOneTaskAndReturnsOnlySafeFields() {
        InMemoryTasks tasks = new InMemoryTasks();
        AdminContentSyncService service = service(tasks, LiveContentSyncPort.Outcome.SUCCESS, 1);

        var response = service.requestSync("request-1", "长沙");

        assertThat(response.syncId()).isEqualTo(700L);
        assertThat(response.cityName()).isEqualTo("长沙");
        assertThat(response.status()).isEqualTo(ContentSyncTaskPort.SyncTaskStatus.FAILED);
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.failureCategory()).isEqualTo(ContentSyncTaskPort.FailureCategory.DATA_VALIDATION);
        assertThat(tasks.created).hasValue(1);
        // providerCityId 虽被任务内部使用，但 REST 安全视图中没有该字段可供序列化。
        assertThat(response).hasNoNullFieldsOrPropertiesExcept("finishedAt");
    }

    @Test
    void givenSameRequestId_whenRetried_thenItReturnsOriginalTaskWithoutASecondProviderRun() {
        InMemoryTasks tasks = new InMemoryTasks();
        AdminContentSyncService service = service(tasks, LiveContentSyncPort.Outcome.SUCCESS, 1);

        service.requestSync("request-2", "长沙");
        var retried = service.requestSync("request-2", "长沙");

        assertThat(retried.status()).isEqualTo(ContentSyncTaskPort.SyncTaskStatus.FAILED);
        assertThat(tasks.created).hasValue(1);
    }

    @Test
    void givenChangshaAndHangzhou_whenAdminSyncs_thenItCallsTheirResolvedProviderCities() {
        InMemoryTasks changshaTasks = new InMemoryTasks();
        InMemoryTasks hangzhouTasks = new InMemoryTasks();
        TrackingCityProvider provider = new TrackingCityProvider(LiveContentSyncPort.Outcome.SUCCESS, 1);

        service(changshaTasks, provider).requestSync("request-changsha", "长沙");
        service(hangzhouTasks, provider).requestSync("request-hangzhou", "杭州");

        // 城市编号只留在 Provider 边界；管理员返回值和任务安全视图均不公开它。
        assertThat(provider.cityCodes).containsExactly("70", "50");
    }

    @Test
    void givenSameRequestId_whenRetried_thenProviderCitySyncRunsOnlyOnce() {
        InMemoryTasks tasks = new InMemoryTasks();
        TrackingCityProvider provider = new TrackingCityProvider(LiveContentSyncPort.Outcome.SUCCESS, 1);
        AdminContentSyncService service = service(tasks, provider);

        service.requestSync("request-idempotent", "长沙");
        service.requestSync("request-idempotent", "长沙");

        assertThat(provider.cityCodes).containsExactly("70");
    }

    @Test
    void givenSameRequestIdWithDifferentCity_whenRetried_thenItReturnsConflict() {
        InMemoryTasks tasks = new InMemoryTasks();
        AdminContentSyncService service = service(tasks, LiveContentSyncPort.Outcome.SUCCESS, 1);
        service.requestSync("request-3", "长沙");

        assertThatThrownBy(() -> service.requestSync("request-3", "杭州"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code()).isEqualTo(100409));
    }

    @Test
    void givenUnknownCity_whenRequested_thenItIsRejectedBeforeProviderIsCalled() {
        InMemoryTasks tasks = new InMemoryTasks();
        TrackingCityProvider provider = new TrackingCityProvider(LiveContentSyncPort.Outcome.SUCCESS, 1);

        assertThatThrownBy(() -> service(tasks, provider).requestSync("request-unknown", "不存在城市"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code()).isEqualTo(100001));
        assertThat(provider.cityCodes).isEmpty();
    }

    @Test
    void givenProviderFailureOutcomes_whenSyncCompletes_thenEachGetsAQueryableFailureCategory() {
        assertFailureCategory(LiveContentSyncPort.Outcome.CONNECTION_FAILED,
                ContentSyncTaskPort.FailureCategory.NETWORK);
        assertFailureCategory(LiveContentSyncPort.Outcome.RATE_LIMITED,
                ContentSyncTaskPort.FailureCategory.RATE_LIMIT);
        assertFailureCategory(LiveContentSyncPort.Outcome.FIELD_REJECTED,
                ContentSyncTaskPort.FailureCategory.DATA_VALIDATION);
    }

    @Test
    void givenExpiredRunningTask_whenQueried_thenItIsRecoveredAsInternalFailureWithoutProviderRetry() {
        InMemoryTasks tasks = new InMemoryTasks();
        tasks.task = new ContentSyncTaskPort.SyncTask(701L, "expired-request", "长沙", "70",
                ContentSyncTaskPort.SyncTaskStatus.RUNNING, LocalDateTime.of(2026, 8, 6, 9, 0), null, 0, 0, null);
        AdminContentSyncService service = service(tasks, LiveContentSyncPort.Outcome.SUCCESS, 1);

        var recovered = service.queryByRequestId("expired-request");

        // 查询原 requestId 会先收敛真正过期任务；绝不重新提交或调用 Provider。
        assertThat(recovered.status()).isEqualTo(ContentSyncTaskPort.SyncTaskStatus.FAILED);
        assertThat(recovered.failureCategory()).isEqualTo(ContentSyncTaskPort.FailureCategory.INTERNAL);
        assertThat(tasks.recovered).hasValue(1);
    }

    private AdminContentSyncService service(InMemoryTasks tasks, LiveContentSyncPort.Outcome outcome, int attempts) {
        return service(tasks, new TrackingCityProvider(outcome, attempts));
    }

    private AdminContentSyncService service(InMemoryTasks tasks, LiveContentSyncPort provider) {
        ContentSyncService content = new ContentSyncService(
                provider,
                snapshotPort(), cachePort(), persistence(), () -> 101L, CLOCK);
        return new AdminContentSyncService(content, tasks,
                new CityResolutionService(new ObjectMapper(), new DefaultResourceLoader()), () -> 700L, CLOCK);
    }

    private void assertFailureCategory(LiveContentSyncPort.Outcome outcome,
                                       ContentSyncTaskPort.FailureCategory expectedCategory) {
        InMemoryTasks tasks = new InMemoryTasks();

        var response = service(tasks, new TrackingCityProvider(outcome, 0)).requestSync("request-" + outcome, "长沙");

        assertThat(response.status()).isEqualTo(ContentSyncTaskPort.SyncTaskStatus.FAILED);
        assertThat(response.failureCategory()).isEqualTo(expectedCategory);
    }

    /** Provider 替身只保留内部 ci 调用痕迹，不包含任何原始响应、Key 或地点文本。 */
    private static final class TrackingCityProvider implements LiveContentSyncPort {
        private final Outcome outcome;
        private final int attemptedCount;
        private final List<String> cityCodes = new ArrayList<>();

        private TrackingCityProvider(Outcome outcome, int attemptedCount) {
            this.outcome = outcome;
            this.attemptedCount = attemptedCount;
        }

        @Override public DailySyncBatch fetchForDailySync() { return batch(); }

        @Override public DailySyncBatch fetchCityCinemas(String cityCode) {
            cityCodes.add(cityCode);
            return batch();
        }

        private DailySyncBatch batch() {
            return new DailySyncBatch(List.of(), attemptedCount, outcome,
                    outcome == Outcome.RATE_LIMITED ? 429 : null);
        }
    }

    private ContentSnapshotPort snapshotPort() {
        return new ContentSnapshotPort() {
            @Override public Optional<ContentResult<List<? extends ContentItem>>> findLatest(ContentQuery query) {
                return Optional.empty();
            }
            @Override public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) { }
        };
    }

    private ContentCachePort cachePort() {
        return new ContentCachePort() {
            @Override public Optional<ContentResult<List<? extends ContentItem>>> find(ContentQuery query) {
                return Optional.empty();
            }
            @Override public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) { }
        };
    }

    private ContentPersistencePort persistence() {
        return new ContentPersistencePort() {
            @Override public long ensureMovie(MovieRow row) { return row.id(); }
            @Override public long ensureCinema(CinemaRow row) { return row.id(); }
            @Override public void insertSnapshot(SnapshotRow row) { }
            @Override public void insertSyncLog(SyncLogRow row) { }
        };
    }

    /** 内存端口只模拟条件更新结果，避免单元测试把 V014 SQL 兼容性误说成 MySQL 验证。 */
    private static final class InMemoryTasks implements ContentSyncTaskPort {
        private final AtomicInteger created = new AtomicInteger();
        private SyncTask task;

        @Override public Optional<SyncTask> findByClientRequestId(String clientRequestId) {
            return task != null && task.clientRequestId().equals(clientRequestId)
                    ? Optional.of(task) : Optional.empty();
        }
        @Override public void createPending(SyncTask pending) {
            task = pending;
            created.incrementAndGet();
        }
        @Override
        public boolean claimPending(long syncId, String leaseOwner, LocalDateTime leaseUntil, LocalDateTime now) {
            task = new SyncTask(task.syncId(), task.clientRequestId(), task.cityName(), task.providerCityId(),
                    SyncTaskStatus.RUNNING, task.startedAt(), null, 0, 0, null);
            return true;
        }
        @Override
        public boolean renewLease(long syncId, String leaseOwner, LocalDateTime leaseUntil, LocalDateTime now) {
            return task != null && task.syncId() == syncId && task.status() == SyncTaskStatus.RUNNING;
        }
        @Override
        public boolean lockAndRenewActiveLease(long syncId, String leaseOwner, LocalDateTime leaseUntil,
                                               LocalDateTime now) {
            return task != null && task.syncId() == syncId && task.status() == SyncTaskStatus.RUNNING;
        }
        @Override public boolean finish(long syncId, String leaseOwner, SyncTaskStatus status, int totalCount,
                                        int successCount, int failureCount, Integer errorCode,
                                        FailureCategory failureCategory, LocalDateTime finishedAt) {
            task = new SyncTask(task.syncId(), task.clientRequestId(), task.cityName(), task.providerCityId(), status,
                    task.startedAt(), finishedAt, successCount, failureCount, failureCategory);
            return true;
        }
        private final AtomicInteger recovered = new AtomicInteger();
        @Override
        public int failExpiredRunningTasks(LocalDateTime now, int errorCode, FailureCategory failureCategory) {
            if (task == null || task.status() != SyncTaskStatus.RUNNING) {
                return 0;
            }
            task = new SyncTask(task.syncId(), task.clientRequestId(), task.cityName(), task.providerCityId(),
                    SyncTaskStatus.FAILED, task.startedAt(), now, 0, 0, failureCategory);
            recovered.incrementAndGet();
            return 1;
        }
        @Override public List<SourceStatus> findLatestSourceStatuses() { return List.of(); }
    }
}
