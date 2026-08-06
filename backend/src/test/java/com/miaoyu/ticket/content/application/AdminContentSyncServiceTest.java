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
        assertThat(tasks.created).hasValue(1);
        // providerCityId 虽被任务内部使用，但 REST 安全视图中没有该字段可供序列化。
        assertThat(response).hasNoNullFieldsOrPropertiesExcept("finishedAt", "failureCategory");
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
    void givenSameRequestIdWithDifferentCity_whenRetried_thenItReturnsConflict() {
        InMemoryTasks tasks = new InMemoryTasks();
        AdminContentSyncService service = service(tasks, LiveContentSyncPort.Outcome.SUCCESS, 1);
        service.requestSync("request-3", "长沙");

        assertThatThrownBy(() -> service.requestSync("request-3", "杭州"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code()).isEqualTo(100409));
    }

    private AdminContentSyncService service(InMemoryTasks tasks, LiveContentSyncPort.Outcome outcome, int attempts) {
        ContentSyncService content = new ContentSyncService(
                () -> new LiveContentSyncPort.DailySyncBatch(List.of(), attempts, outcome,
                        outcome == LiveContentSyncPort.Outcome.RATE_LIMITED ? 429 : null),
                snapshotPort(), cachePort(), persistence(), () -> 101L, CLOCK);
        return new AdminContentSyncService(content, tasks,
                new CityResolutionService(new ObjectMapper(), new DefaultResourceLoader()), () -> 700L, CLOCK);
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
        @Override public boolean finish(long syncId, String leaseOwner, SyncTaskStatus status, int totalCount,
                                        int successCount, int failureCount, Integer errorCode,
                                        FailureCategory failureCategory, LocalDateTime finishedAt) {
            task = new SyncTask(task.syncId(), task.clientRequestId(), task.cityName(), task.providerCityId(), status,
                    task.startedAt(), finishedAt, successCount, failureCount, failureCategory);
            return true;
        }
        @Override public List<SourceStatus> findLatestSourceStatuses() { return List.of(); }
    }
}
