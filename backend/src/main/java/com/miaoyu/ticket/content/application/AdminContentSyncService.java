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
        // 恢复过期任务放在创建前，防止旧 RUNNING 记录长期占住同一请求标识。
        validateRequest(clientRequestId, cityName);
        recoverExpiredSyncTasks();
        ContentSyncTaskPort.SyncTask existing = taskPort.findByClientRequestId(clientRequestId).orElse(null);
        if (existing != null) {
            // 已有任务直接返回，不因为管理员重复点击重新申请租约或访问第三方。
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
            // PENDING 先成功落库，浏览器断网时仍可用原请求标识查询唯一结果。
            taskPort.createPending(pending);
        } catch (DuplicateKeyException duplicate) {
            // 并发的同请求提交只允许查询已经存在的记录，不能顺势再走一次 Provider。
            return taskPort.findByClientRequestId(clientRequestId).map(this::viewOf).orElseThrow(() -> duplicate);
        }
        String leaseOwner = UUID.randomUUID().toString();
        if (!taskPort.claimPending(pending.syncId(), leaseOwner, startedAt.plusSeconds(90), startedAt)) {
            // 条件更新落空时可能被并发实例取得；只读取现状，不能补发一次 Provider 请求。
            return queryByRequestId(clientRequestId);
        }
        AtomicBoolean leaseActive = new AtomicBoolean(true);
        ScheduledFuture<?> renewal = startLeaseRenewal(pending.syncId(), leaseOwner, leaseActive);
        try {
            ContentSyncService.CurrentHotMovieSyncResult result =
                    contentSyncService.synchronizeCityCinemasWithResult(cityName, providerCityId,
                            () -> lockAndRenewActiveLease(pending.syncId(), leaseOwner, leaseActive));
            LocalDateTime finishedAt = now();
            ContentSyncTaskPort.SyncTaskStatus status = taskStatus(result);
            ContentSyncTaskPort.FailureCategory category = failureCategory(result.outcome(), status);
            taskPort.finish(pending.syncId(), leaseOwner, status, result.totalCount(), result.successCount(),
                    result.failureCount(), errorCodeOf(result, status), category, finishedAt);
        } catch (ContentSyncService.LeaseLostException lostLease) {
            // 旧 Worker 已失去写入资格，不能再次改写被接管或已收敛任务的终态。
            // 任务会由当前持有者续租完成，或在租约真正到期后由恢复器收敛。
        } catch (RuntimeException exception) {
            // 外部调用或内容持久化异常必须收敛为可查询终态，不能把 requestId 永久留在 RUNNING。
            LocalDateTime failedAt = now();
            taskPort.finish(pending.syncId(), leaseOwner, ContentSyncTaskPort.SyncTaskStatus.FAILED,
                    0, 0, 0, 303004, ContentSyncTaskPort.FailureCategory.INTERNAL, failedAt);
        } finally {
            // 结束后停止本实例的续租，避免已完成任务被后台线程意外延长。
            if (renewal != null) {
                renewal.cancel(false);
            }
        }
        return queryByRequestId(clientRequestId);
    }

    /** 超时恢复只读取原审计记录；不存在时返回固定 404，不以最近同步替代。 */
    public SyncTaskView queryByRequestId(String clientRequestId) {
        // 查询也做恢复，使管理员不必等待定时恢复器才能看到已到期的最终失败状态。
        if (clientRequestId == null || clientRequestId.isBlank() || clientRequestId.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "clientRequestId不合法");
        }
        recoverExpiredSyncTasks();
        return taskPort.findByClientRequestId(clientRequestId)
                .map(this::viewOf)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "同步请求不存在"));
    }

    /** 来源列表只传递脱敏汇总，避免 Controller 因新增字段泄漏城市编号或租约。 */
    public List<ContentSyncTaskPort.SourceStatus> sourceStatuses() { return taskPort.findLatestSourceStatuses(); }

    /** 定时入口和查询入口共享同一恢复规则，保证没有管理员访问时也会收敛过期任务。 */
    public int recoverExpiredSyncTasks() {
        // 统一使用当前业务时钟，测试和生产不会因 JVM 默认时区不同提前恢复。
        // 端口内部按租约条件更新，调用多次不会重复改写终态。
        return taskPort.failExpiredRunningTasks(now(), 303004, ContentSyncTaskPort.FailureCategory.INTERNAL);
    }

    /** 存活同步每二十秒续到当前时间后九十秒；没有调度器时由 Provider 的短超时保证不会长期占用。 */
    private ScheduledFuture<?> startLeaseRenewal(long syncId, String leaseOwner, AtomicBoolean leaseActive) {
        // 没有调度器的纯单元测试不创建后台线程，生产环境仍由 Spring TaskScheduler 执行续租。
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
        // 请求标识是管理员在网络结果未知时唯一可恢复的凭据，空值会导致后续无法安全查询原任务。
        // 城市名只接受短文本；Provider 城市编号由服务端目录查出，不能由 HTTP 参数绕过目录校验。
        if (clientRequestId == null || clientRequestId.isBlank() || clientRequestId.length() > 64
                || cityName == null || cityName.isBlank() || cityName.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "同步请求参数不合法");
        }
    }

    private ContentSyncTaskPort.SyncTaskStatus taskStatus(ContentSyncService.CurrentHotMovieSyncResult result) {
        // 任一非 SUCCESS outcome 即使尚未拿到候选数也不是成功，避免断网/限流被显示为 SUCCESS。
        // 这里不依据 HTTP 状态码猜测异常；Provider 已在自己的边界把异常归成固定 Outcome。
        if (result.outcome() != LiveContentSyncPort.Outcome.SUCCESS) {
            return ContentSyncTaskPort.SyncTaskStatus.FAILED;
        }
        if (result.successCount() == 0 && result.totalCount() > 0) {
            // 拿到候选但一条都不能落库时，管理员需要看到失败而不是“本轮无更新”。
            // 失败数量仍由同步服务保留，不能为了状态好看把它归零。
            return ContentSyncTaskPort.SyncTaskStatus.FAILED;
        }
        // 已成功与失败并存才是 PARTIAL；零候选的正常成功仍允许写 SUCCESS。
        return result.failureCount() == 0 ? ContentSyncTaskPort.SyncTaskStatus.SUCCESS
                : ContentSyncTaskPort.SyncTaskStatus.PARTIAL;
    }

    private ContentSyncTaskPort.FailureCategory failureCategory(LiveContentSyncPort.Outcome outcome,
                                                                ContentSyncTaskPort.SyncTaskStatus status) {
        // 管理端只需固定失败分类来提示重试时机，不需要也不能接触第三方异常正文。
        // 数据质量失败包括 Provider 已响应，但个别影院字段或身份不满足本地最小规则的情形。
        return switch (outcome) {
            // 成功响应中仍可能有被字段质量或身份规则拒绝的候选；这是 PARTIAL/FAILED 的数据质量失败。
            case SUCCESS -> status == ContentSyncTaskPort.SyncTaskStatus.SUCCESS ? null
                    : ContentSyncTaskPort.FailureCategory.DATA_VALIDATION;
            case PROVIDER_DISABLED -> ContentSyncTaskPort.FailureCategory.INTERNAL;
            case CONNECTION_FAILED -> ContentSyncTaskPort.FailureCategory.NETWORK;
            case RATE_LIMITED -> ContentSyncTaskPort.FailureCategory.RATE_LIMIT;
            case UPSTREAM_FAILED -> ContentSyncTaskPort.FailureCategory.PROVIDER_RESPONSE;
            case FIELD_REJECTED -> ContentSyncTaskPort.FailureCategory.DATA_VALIDATION;
        };
    }

    private Integer errorCodeOf(ContentSyncService.CurrentHotMovieSyncResult result,
                                ContentSyncTaskPort.SyncTaskStatus status) {
        // SUCCESS 的错误字段必须为空，避免 V015 以后被数据库拒绝，也避免页面误报失败。
        return status == ContentSyncTaskPort.SyncTaskStatus.SUCCESS ? null
                // Provider 未给出安全的固定错误码时，统一使用内容不可用，不传播网络库异常。
                : result.errorCode() == null ? 303004 : result.errorCode();
    }

    /**
     * 将内部任务投影为 REST 可见的最小字段。
     *
     * <p>providerCityId 和 leaseOwner 只能用于 Provider 调用及条件更新；即使以后任务记录增加字段，
     * 也必须显式加入本视图后才能对外出现，防止 record 自动序列化泄漏内部信息。</p>
     */
    private SyncTaskView viewOf(ContentSyncTaskPort.SyncTask task) {
        return new SyncTaskView(task.syncId(), task.clientRequestId(), task.cityName(), task.status(), task.startedAt(),
                task.finishedAt(), task.successCount(), task.failureCount(), task.failureCategory());
    }

    /** 所有同步时间都使用统一业务时区，避免凌晨调度与审计页面因服务器时区不同产生错日。 */
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    /** REST 适配层只依赖此安全视图。 */
    public record SyncTaskView(long syncId, String clientRequestId, String cityName,
                               ContentSyncTaskPort.SyncTaskStatus status, LocalDateTime startedAt,
                               LocalDateTime finishedAt, int successCount, int failureCount,
                               ContentSyncTaskPort.FailureCategory failureCategory) { }
}
