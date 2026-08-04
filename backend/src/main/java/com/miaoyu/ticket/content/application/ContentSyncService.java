package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
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
        List<LiveContentSyncPort.SynchronizedContent> contents = provider.fetchForDailySync();
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
        persistence.insertSyncLog(new ContentPersistencePort.SyncLogRow(idGenerator.nextId(), "NETSTART_MAOYAN",
                "DAILY_CONTENT", "daily-" + startedAt, ContentPersistencePort.SyncStatus.SUCCESS, null,
                contents.size(), synchronizedCount, contents.size() - synchronizedCount, startedAt, finishedAt, null));
        return synchronizedCount;
    }
}
