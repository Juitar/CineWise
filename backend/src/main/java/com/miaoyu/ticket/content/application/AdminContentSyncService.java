package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/**
 * 管理员受控同步用例。
 *
 * <p>它先登记 PENDING，再取得租约，最后调用已有的影片同步服务。页面超时或断网后只能按原
 * clientRequestId 查询本服务，不能用新标识再次 POST，避免同一城市在多个实例重复访问 Provider。</p>
 */
@Service
public class AdminContentSyncService {
    private static final String PROVIDER = "NETSTART_MAOYAN";
    private final ContentSyncService contentSyncService;
    private final ContentSyncTaskPort taskPort;
    private final CityResolutionService cityResolutionService;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;
    private final ObjectProvider<TaskScheduler> taskSchedulerProvider;

    @Autowired
    public AdminContentSyncService(ContentSyncService contentSyncService, ContentSyncTaskPort taskPort,
                                   CityResolutionService cityResolutionService, BusinessIdGenerator idGenerator,
                                   Clock clock,
                                   @Qualifier("taskScheduler") ObjectProvider<TaskScheduler> taskSchedulerProvider) {
        this.contentSyncService = contentSyncService;
        this.taskPort = taskPort;
        this.cityResolutionService = cityResolutionService;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.taskSchedulerProvider = taskSchedulerProvider;
    }

    /** 测试构造器不启动后台续租，测试通过显式调用端口模拟租约边界。 */
    AdminContentSyncService(ContentSyncService contentSyncService, ContentSyncTaskPort taskPort,
                            CityResolutionService cityResolutionService, BusinessIdGenerator idGenerator, Clock clock) {
        this(contentSyncService, taskPort, cityResolutionService, idGenerator, clock, null);
    }

    /**
     * 受理管理员请求并同步本轮当前热映详情。
     *
     * <p>城市只用来校验本地目录与记录审计范围；当前热映目录是全国影片基础资料，不据此编造城市排期。
     * Provider 城市编号仅保存在数据库内部字段，返回对象从不携带它。</p>
     */
    public SyncTaskView requestSync(String clientRequestId, String cityName) {
        validateRequest(clientRequestId, cityName);
        recoverExpiredSyncTasks();
        ContentSyncTaskPort.SyncTask existing = taskPort.findByClientRequestId(clientRequestId).orElse(null);
        if (existing != null) {
            if (!existing.cityName().equals(cityName)) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "同一请求标识不能切换城市");
            }
            return viewOf(existing);
        }
        String providerCityId = cityResolutionService.findProviderCityId(cityName)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_PARAMETER, "城市不在本地目录中"));
        LocalDateTime startedAt = now();
        ContentSyncTaskPort.SyncTask pending = new ContentSyncTaskPort.SyncTask(idGenerator.nextId(), clientRequestId,
                cityName, providerCityId, ContentSyncTaskPort.SyncTaskStatus.PENDING, startedAt, null, 0, 0, null);
        try {
            taskPort.createPending(pending);
        } catch (DuplicateKeyException duplicate) {
            // 并发的同请求提交只允许查询已经存在的记录，不能顺势再走一次 Provider。
            return taskPort.findByClientRequestId(clientRequestId).map(this::viewOf).orElseThrow(() -> duplicate);
        }
        String leaseOwner = UUID.randomUUID().toString();
        if (!taskPort.claimPending(pending.syncId(), leaseOwner, startedAt.plusSeconds(90), startedAt)) {
            return queryByRequestId(clientRequestId);
        }
        AtomicBoolean leaseActive = new AtomicBoolean(true);
        ScheduledFuture<?> renewal = startLeaseRenewal(pending.syncId(), leaseOwner, leaseActive);
        try {
            ContentSyncService.CurrentHotMovieSyncResult result =
                    contentSyncService.synchronizeCurrentHotMoviesWithResult(
                            () -> lockAndRenewActiveLease(pending.syncId(), leaseOwner, leaseActive));
            LocalDateTime finishedAt = now();
            ContentSyncTaskPort.SyncTaskStatus status = taskStatus(result);
            ContentSyncTaskPort.FailureCategory category = failureCategory(result.outcome());
            taskPort.finish(pending.syncId(), leaseOwner, status, result.totalCount(), result.successCount(),
                    result.failureCount(), errorCodeOf(result, status), category, finishedAt);
        } catch (ContentSyncService.LeaseLostException lostLease) {
            // 旧 Worker 已失去写入资格，不能再次改写被接管或已收敛任务的终态。
        } catch (RuntimeException exception) {
            // 外部调用或内容持久化异常必须收敛为可查询终态，不能把 requestId 永久留在 RUNNING。
            LocalDateTime failedAt = now();
            taskPort.finish(pending.syncId(), leaseOwner, ContentSyncTaskPort.SyncTaskStatus.FAILED,
                    0, 0, 0, 303004, ContentSyncTaskPort.FailureCategory.INTERNAL, failedAt);
        } finally {
            if (renewal != null) {
                renewal.cancel(false);
            }
        }
        return queryByRequestId(clientRequestId);
    }

    /** 超时恢复只读取原审计记录；不存在时返回固定 404，不以最近同步替代。 */
    public SyncTaskView queryByRequestId(String clientRequestId) {
        if (clientRequestId == null || clientRequestId.isBlank() || clientRequestId.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "clientRequestId不合法");
        }
        recoverExpiredSyncTasks();
        return taskPort.findByClientRequestId(clientRequestId)
                .map(this::viewOf)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "同步请求不存在"));
    }

    /** 来源列表刻意只传递安全视图，避免 Controller 因新增字段意外泄漏内部城市编号或租约。 */
    public List<ContentSyncTaskPort.SourceStatus> sourceStatuses() { return taskPort.findLatestSourceStatuses(); }

    /** 定时入口和查询入口共享同一恢复规则，保证没有管理员访问时也会收敛过期任务。 */
    public int recoverExpiredSyncTasks() {
        return taskPort.failExpiredRunningTasks(now(), 303004, ContentSyncTaskPort.FailureCategory.INTERNAL);
    }

    /** 存活同步每二十秒续到当前时间后九十秒；没有调度器时由 Provider 的短超时保证不会长期占用。 */
    private ScheduledFuture<?> startLeaseRenewal(long syncId, String leaseOwner, AtomicBoolean leaseActive) {
        TaskScheduler scheduler = taskSchedulerProvider == null ? null : taskSchedulerProvider.getIfAvailable();
        if (scheduler == null) {
            return null;
        }
        return scheduler.scheduleAtFixedRate(() -> {
            LocalDateTime currentTime = now();
            if (!taskPort.renewLease(syncId, leaseOwner, currentTime.plusSeconds(90), currentTime)) {
                // 续租失败后先在本地阻止写入；资料事务会再向数据库复核一次。
                leaseActive.set(false);
            }
        }, Duration.ofSeconds(20));
    }

    /** 资料事务内的条件续租同时取得任务行锁，直到该事务提交或回滚才允许恢复器继续。 */
    private boolean lockAndRenewActiveLease(long syncId, String leaseOwner, AtomicBoolean leaseActive) {
        LocalDateTime currentTime = now();
        return leaseActive.get() && taskPort.lockAndRenewActiveLease(syncId, leaseOwner,
                currentTime.plusSeconds(90), currentTime);
    }

    private void validateRequest(String clientRequestId, String cityName) {
        if (clientRequestId == null || clientRequestId.isBlank() || clientRequestId.length() > 64
                || cityName == null || cityName.isBlank() || cityName.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "同步请求参数不合法");
        }
    }

    private ContentSyncTaskPort.SyncTaskStatus taskStatus(ContentSyncService.CurrentHotMovieSyncResult result) {
        // Provider 被环境开关拒绝时没有候选数，但管理员任务并没有成功完成，不能显示为 SUCCESS。
        if (result.outcome() == LiveContentSyncPort.Outcome.PROVIDER_DISABLED) {
            return ContentSyncTaskPort.SyncTaskStatus.FAILED;
        }
        if (result.successCount() == 0 && result.totalCount() > 0) {
            return ContentSyncTaskPort.SyncTaskStatus.FAILED;
        }
        return result.failureCount() == 0 ? ContentSyncTaskPort.SyncTaskStatus.SUCCESS
                : ContentSyncTaskPort.SyncTaskStatus.PARTIAL;
    }

    private ContentSyncTaskPort.FailureCategory failureCategory(LiveContentSyncPort.Outcome outcome) {
        return switch (outcome) {
            case SUCCESS -> null;
            case PROVIDER_DISABLED -> ContentSyncTaskPort.FailureCategory.INTERNAL;
            case CONNECTION_FAILED -> ContentSyncTaskPort.FailureCategory.NETWORK;
            case RATE_LIMITED -> ContentSyncTaskPort.FailureCategory.RATE_LIMIT;
            case UPSTREAM_FAILED -> ContentSyncTaskPort.FailureCategory.PROVIDER_RESPONSE;
            case FIELD_REJECTED -> ContentSyncTaskPort.FailureCategory.DATA_VALIDATION;
        };
    }

    private Integer errorCodeOf(ContentSyncService.CurrentHotMovieSyncResult result,
                                ContentSyncTaskPort.SyncTaskStatus status) {
        return status == ContentSyncTaskPort.SyncTaskStatus.SUCCESS ? null
                : result.errorCode() == null ? 303004 : result.errorCode();
    }

    private SyncTaskView viewOf(ContentSyncTaskPort.SyncTask task) {
        return new SyncTaskView(task.syncId(), task.clientRequestId(), task.cityName(), task.status(), task.startedAt(),
                task.finishedAt(), task.successCount(), task.failureCount(), task.failureCategory());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    /** REST 适配层只依赖此安全视图。 */
    public record SyncTaskView(long syncId, String clientRequestId, String cityName,
                               ContentSyncTaskPort.SyncTaskStatus status, LocalDateTime startedAt,
                               LocalDateTime finishedAt, int successCount, int failureCount,
                               ContentSyncTaskPort.FailureCategory failureCategory) { }
}
