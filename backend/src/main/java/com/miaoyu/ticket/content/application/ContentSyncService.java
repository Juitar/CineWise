package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 每日内容同步用例；它只保存标准化结果，页面请求不会调用外部 Provider。
 */
@Service
public class ContentSyncService {
    /** Provider 在自身边界完成网络控制和字段校验，服务不会接收原始响应。 */
    private final LiveContentSyncPort provider;
    /** MySQL 快照是 Redis 清理、应用重启后仍可追溯的真实资料来源。 */
    private final ContentSnapshotPort snapshots;
    /** Redis 只加速刚同步过的真实结果，写入失败不影响快照已经保存的内容。 */
    private final ContentCachePort cache;
    /** 同步审计只登记结果数量和时间，避免将第三方完整载荷扩散到日志表。 */
    private final ContentPersistencePort persistence;
    /** 审计记录使用 D 的业务 ID，不能复用影片或影院的来源 ID。 */
    private final BusinessIdGenerator idGenerator;
    /** 统一业务时钟保证同步批次号和审计时间在测试、部署环境可复现。 */
    private final Clock clock;

    public ContentSyncService(LiveContentSyncPort provider, ContentSnapshotPort snapshots, ContentCachePort cache,
                              ContentPersistencePort persistence, BusinessIdGenerator idGenerator, Clock clock) {
        this.provider = provider;
        this.snapshots = snapshots;
        this.cache = cache;
        this.persistence = persistence;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * 按每天一次的受控入口同步真实内容。
     *
     * <p>先由 Provider 返回完全标准化的 LIVE 结果，再覆盖快照和对应 Redis 键。页面查询从不走这条
     * 网络路径；如果 Provider 关闭或外网失败，它只会返回零条，旧快照和 Demo 会保留给查询服务回退。</p>
     *
     * <p>写入顺序固定为快照在前、缓存随后：即使 Redis 写入失败，下一次页面查询仍能读取刚更新的真实
     * 快照；Redis 成功时则覆盖同一查询键，避免旧缓存压过新快照。</p>
     */
    @Transactional
    public int synchronizeDailyContent() {
        LocalDateTime startedAt = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        LiveContentSyncPort.DailySyncBatch batch = provider.fetchForDailySync();
        List<LiveContentSyncPort.SynchronizedContent> contents = batch.contents();
        int synchronizedCount = 0;
        for (LiveContentSyncPort.SynchronizedContent content : contents) {
            // 端口的契约要求 LIVE；再次校验可防止错误实现把 Demo 或旧快照污染真实读取层。
            if (content.result().source().type() != ContentSourceType.LIVE) {
                continue;
            }
            snapshots.save(content.query(), content.result());
            cache.save(content.query(), content.result());
            synchronizedCount++;
        }
        // 审计只保存统计值和固定状态，不保存 Provider 原始 JSON、关键词或任何用户数据。
        LocalDateTime finishedAt = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        int failureCount = Math.max(0, batch.attemptedCount() - synchronizedCount);
        persistence.insertSyncLog(new ContentPersistencePort.SyncLogRow(idGenerator.nextId(), "NETSTART_MAOYAN",
                "DAILY_CONTENT", "daily-" + startedAt, statusOf(batch, synchronizedCount, failureCount),
                batch.errorCode(), batch.attemptedCount(), synchronizedCount, failureCount, startedAt, finishedAt,
                auditSummary(batch, contents, synchronizedCount, failureCount, startedAt, finishedAt)));
        return synchronizedCount;
    }

    /** 数据库现有四种状态已足够表达本轮结果，无须为了审计摘要新增字段或枚举值。 */
    private ContentPersistencePort.SyncStatus statusOf(LiveContentSyncPort.DailySyncBatch batch,
                                                        int synchronizedCount, int failureCount) {
        if (batch.attemptedCount() == 0) {
            return ContentPersistencePort.SyncStatus.SUCCESS;
        }
        if (synchronizedCount == 0) {
            return ContentPersistencePort.SyncStatus.FAILED;
        }
        return failureCount == 0 ? ContentPersistencePort.SyncStatus.SUCCESS
                : ContentPersistencePort.SyncStatus.PARTIAL;
    }

    /**
     * 复用 `error_summary` 写入固定键值的脱敏审计摘要，字段不够时也不记录原始 Provider 内容。
     *
     * <p>来源、资源、状态、耗时和计数已经分别保存在结构化列；这里补充数据时间、质量计数和降级层级。
     * 影片标题、影院地址、搜索词、影评和任何 HTTP 响应正文均不允许进入该字符串。</p>
     */
    private String auditSummary(LiveContentSyncPort.DailySyncBatch batch,
                                List<LiveContentSyncPort.SynchronizedContent> contents,
                                int synchronizedCount, int failureCount, LocalDateTime startedAt,
                                LocalDateTime finishedAt) {
        Optional<LocalDateTime> latestDataTime = contents.stream()
                .map(content -> content.result().dataTime()).max(LocalDateTime::compareTo);
        String fallback = contents.stream().anyMatch(content -> content.result().degraded())
                ? "DEGRADED" : "NONE";
        long elapsedMillis = java.time.Duration.between(startedAt, finishedAt).toMillis();
        return "outcome=" + batch.outcome() + ";elapsedMs=" + elapsedMillis + ";dataTime="
                + latestDataTime.map(LocalDateTime::toString).orElse("NONE") + ";quality=accepted:"
                + synchronizedCount + ",rejected:" + failureCount + ";fallback=" + fallback;
    }
}
