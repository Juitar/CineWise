package com.miaoyu.ticket.content.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 管理员同步任务的审计读写端口。
 *
 * <p>V014 已为 data_sync_log 增加任务状态、城市、失败分类和租约字段。本端口只处理脱敏后的
 * 任务状态，不保存 Provider URL、原始响应、地点文本或内部城市编号的公开形式。</p>
 */
public interface ContentSyncTaskPort {

    /** 相同 clientRequestId 的查询用于超时后的恢复，不能据此发起第二次 Provider 调用。 */
    Optional<SyncTask> findByClientRequestId(String clientRequestId);

    /** 写入 PENDING 前不访问 Provider，失败时调用方必须以原请求标识继续查询。 */
    void createPending(SyncTask task);

    /** 只有仍为 PENDING 的记录可被一个随机持有者取得运行租约。 */
    boolean claimPending(long syncId, String leaseOwner, LocalDateTime leaseUntil, LocalDateTime now);

    /** 存活持有者每二十秒延长租约；返回 false 表示已经失去写入资格。 */
    boolean renewLease(long syncId, String leaseOwner, LocalDateTime leaseUntil, LocalDateTime now);

    /**
     * 在资料真正写入前再次确认当前实例仍持有未过期租约。
     *
     * <p>续租成功只说明某个时刻曾持有租约；慢 Provider 返回后仍要复核，避免已被恢复任务收敛或接管的旧 Worker 覆盖公开资料。</p>
     */
    boolean holdsActiveLease(long syncId, String leaseOwner, LocalDateTime now);

    /** 终态写入必须仍匹配未过期持有者，避免慢请求覆盖已经被恢复的任务。 */
    boolean finish(long syncId, String leaseOwner, SyncTaskStatus status, int totalCount, int successCount,
                   int failureCount, Integer errorCode, FailureCategory failureCategory, LocalDateTime finishedAt);

    /** 只把已经真正过期的 RUNNING 任务收敛为失败，不重新调用 Provider。 */
    int failExpiredRunningTasks(LocalDateTime now, int errorCode, FailureCategory failureCategory);

    /** 管理页面只读取已经脱敏的审计视图。 */
    List<SourceStatus> findLatestSourceStatuses();

    /** 同步任务内部视图保留 providerCityId，只供 Provider 边界使用，不进入 REST DTO。 */
    record SyncTask(long syncId, String clientRequestId, String cityName, String providerCityId,
                    SyncTaskStatus status, LocalDateTime startedAt, LocalDateTime finishedAt,
                    int successCount, int failureCount, FailureCategory failureCategory) { }

    /** 管理来源列表不暴露 Provider 城市编号、租约持有者、请求标识或异常正文。 */
    record SourceStatus(String provider, String resourceType, String cityName, SyncTaskStatus status,
                        LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime lastSuccessAt,
                        int successCount, int failureCount, FailureCategory failureCategory,
                        LocalDateTime dataTime, LocalDateTime expiresAt) { }

    enum SyncTaskStatus { PENDING, RUNNING, SUCCESS, PARTIAL, FAILED }

    /** 固定失败分类可供页面提示，但不足以反推出第三方响应正文。 */
    enum FailureCategory { NETWORK, RATE_LIMIT, PROVIDER_RESPONSE, DATA_VALIDATION, INTERNAL }
}
