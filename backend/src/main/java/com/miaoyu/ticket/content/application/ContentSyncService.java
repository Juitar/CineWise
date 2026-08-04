package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 每日内容同步用例；它只保存标准化结果，页面请求不会调用外部 Provider。
 *
 * <p>同步的目标是补充影片和影院的基础资料，不是生成票务事实。
 * A 维护的场次、价格、库存和座位不会从这里写入或推断。</p>
 *
 * <p>每个候选项先经过身份隔离，再取得本库的业务 ID，最后写入快照。
 * 这样公开 REST 接口只面对稳定的内部 ID，外部 ID 仍只作为来源追溯信息。</p>
 *
 * <p>缓存不是数据库事实，所以只在事务提交后更新；事务回滚时由快照保持原状。
 * 同步失败或被关闭时，本服务不清理旧快照，查询服务会继续按既定顺序降级。</p>
 *
 * <p>拒绝项只写固定审计分类和数量，不保存完整原始响应或候选匹配细节。
 * 人工复核需要的信息应从受控的 Provider 质量记录取得，不能借同步日志泄漏数据。</p>
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
    /** 事务模板只包裹数据库写入，外部 Provider 调用必须在 execute 之前完成。 */
    private final TransactionTemplate transactionTemplate;
    /** 同步前复用同一批次身份规则，避免重复外部 ID 或同名不同 ID 覆盖已有真实资料。 */
    private final ContentIdentityPolicy identityPolicy = new ContentIdentityPolicy();

    @Autowired
    public ContentSyncService(LiveContentSyncPort provider, ContentSnapshotPort snapshots, ContentCachePort cache,
                              ContentPersistencePort persistence, BusinessIdGenerator idGenerator, Clock clock,
                              PlatformTransactionManager transactionManager) {
        this(provider, snapshots, cache, persistence, idGenerator, clock, new TransactionTemplate(transactionManager));
    }

    /** 测试构造器不创建 Spring 事务；生产构造器始终注入事务管理器。 */
    ContentSyncService(LiveContentSyncPort provider, ContentSnapshotPort snapshots, ContentCachePort cache,
                       ContentPersistencePort persistence, BusinessIdGenerator idGenerator, Clock clock) {
        this(provider, snapshots, cache, persistence, idGenerator, clock, (TransactionTemplate) null);
    }

    private ContentSyncService(LiveContentSyncPort provider, ContentSnapshotPort snapshots, ContentCachePort cache,
                               ContentPersistencePort persistence, BusinessIdGenerator idGenerator, Clock clock,
                               TransactionTemplate transactionTemplate) {
        this.provider = provider;
        this.snapshots = snapshots;
        this.cache = cache;
        this.persistence = persistence;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
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
    public int synchronizeDailyContent() {
        LocalDateTime startedAt = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        LiveContentSyncPort.DailySyncBatch batch = provider.fetchForDailySync();
        // Spring 文档规定 TransactionTemplate 的 execute 回调才处于事务中；Provider 已在其外完成网络调用。
        return transactionTemplate == null ? persistBatch(batch, startedAt)
                : transactionTemplate.execute(status -> persistBatch(batch, startedAt));
    }

    /** 该方法仅被事务模板回调调用，保证业务 ID、快照和审计日志要么一起提交，要么一起回滚。 */
    private int persistBatch(LiveContentSyncPort.DailySyncBatch batch, LocalDateTime startedAt) {
        List<LiveContentSyncPort.SynchronizedContent> contents = batch.contents();
        List<ContentItem> allItems = contents.stream().flatMap(content -> content.result().data().stream())
                .map(item -> (ContentItem) item).toList();
        ContentIdentityPolicy.Decision identityDecision = identityPolicy.decide("NETSTART_MAOYAN", allItems);
        java.util.Set<ContentItem> acceptedItems = Collections.newSetFromMap(new IdentityHashMap<>());
        acceptedItems.addAll(identityDecision.accepted());
        int synchronizedItemCount = 0;
        int identityRejectedCount = identityDecision.rejected().size();
        for (LiveContentSyncPort.SynchronizedContent content : contents) {
            // 端口的契约要求 LIVE；再次校验可防止错误实现把 Demo 或旧快照污染真实读取层。
            if (content.result().source().type() != ContentSourceType.LIVE) {
                continue;
            }
            List<ContentItem> acceptedForQuery = content.result().data().stream()
                    .filter(acceptedItems::contains).map(item -> persistAndAttachBusinessId(item, content.result()))
                    .toList();
            if (acceptedForQuery.isEmpty()) {
                continue;
            }
            ContentResult<List<? extends com.miaoyu.ticket.content.domain.ContentItem>> accepted = new ContentResult<>(
                    acceptedForQuery, content.result().source(), content.result().dataTime(),
                    content.result().expiresAt(), content.result().expired(), content.result().degraded(),
                    content.result().fallbackType());
            snapshots.save(content.query(), accepted);
            writeCacheAfterCommit(content.query(), accepted);
            synchronizedItemCount += acceptedForQuery.size();
        }
        // 审计只保存统计值和固定状态，不保存 Provider 原始 JSON、关键词或任何用户数据。
        LocalDateTime finishedAt = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        // V004 的计数约束要求 total = success + failure。Provider 的候选数与身份隔离后的条目数取较大值，
        // 既保留上游字段拒绝/请求失败，也保证额外隔离项不会让审计日志在真实 MySQL 中被 CHECK 拒绝。
        int totalItemCount = Math.max(batch.attemptedCount(), synchronizedItemCount + identityRejectedCount);
        int failureCount = totalItemCount - synchronizedItemCount;
        persistence.insertSyncLog(new ContentPersistencePort.SyncLogRow(idGenerator.nextId(), "NETSTART_MAOYAN",
                "DAILY_CONTENT", "daily-" + startedAt, statusOf(totalItemCount, synchronizedItemCount, failureCount),
                batch.errorCode(), totalItemCount, synchronizedItemCount, failureCount, startedAt, finishedAt,
                auditSummary(batch, contents, synchronizedItemCount, failureCount, identityRejectedCount, startedAt,
                        finishedAt)));
        return synchronizedItemCount;
    }

    /**
     * 真实内容表的内部业务 ID 必须先由幂等写入取得，再进入公开查询可读的快照与缓存。
     *
     * <p>不能把 NetStart 的来源 ID 当作 REST 的业务 ID：来源可能变化、重复或被替换，
     * 而 A、C 和前端只认数据库返回的十进制业务 ID。</p>
     *
     * <p>仅影片和影院两种 sealed 内容会进入本同步入口；若以后新增资源类型，
     * 必须先补 OpenSpec、持久化规则和公开 DTO，不能在这里静默按影院处理。</p>
     *
     * <p>持久化适配器以来源 ID 做幂等查询，重复同步只更新资料和时效，不创建新的业务对象。
     * 这保证前端已持有的 movieId/cinemaId 在 Provider 再次同步后仍然有效。</p>
     */
    private ContentItem persistAndAttachBusinessId(ContentItem item, ContentResult<?> result) {
        if (item instanceof MovieContent movie) {
            long movieId = persistence.ensureMovie(new ContentPersistencePort.MovieRow(idGenerator.nextId(),
                    movie.sourceMovieId(), movie.title(), movie.genresJson(), movie.durationMinutes(), movie.rating(),
                    result.source().type(), result.source().name(), result.dataTime(), result.expiresAt()));
            return new MovieContent(movieId, movie.sourceMovieId(), movie.title(), movie.genresJson(),
                    movie.durationMinutes(), movie.rating());
        }
        CinemaContent cinema = (CinemaContent) item;
        long cinemaId = persistence.ensureCinema(new ContentPersistencePort.CinemaRow(idGenerator.nextId(),
                cinema.sourceCinemaId(), cinema.name(), cinema.cityCode(), cinema.area(), cinema.address(),
                cinema.longitude(), cinema.latitude(), result.source().type(), result.source().name(),
                result.dataTime(), result.expiresAt()));
        return new CinemaContent(cinemaId, cinema.sourceCinemaId(), cinema.name(), cinema.cityCode(), cinema.area(),
                cinema.address(), cinema.longitude(), cinema.latitude());
    }

    /**
     * Redis 不是事务事实；只有 MySQL 成功提交后才允许发布新的 LIVE 缓存。
     *
     * <p>事务同步已激活时，afterCommit 前绝不调用缓存端口，避免数据库回滚后页面仍读到
     * 无法追溯的 LIVE 内容。单元测试或无事务调用则直接写缓存，保持端口的可独立测试性。</p>
     */
    private void writeCacheAfterCommit(ContentQuery query, ContentResult<List<? extends ContentItem>> result) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.save(query, result);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache.save(query, result); }
        });
    }

    /** 数据库现有四种状态已足够表达本轮结果，无须为了审计摘要新增字段或枚举值。 */
    private ContentPersistencePort.SyncStatus statusOf(int totalItemCount, int synchronizedItemCount,
                                                        int failureCount) {
        if (totalItemCount == 0) {
            return ContentPersistencePort.SyncStatus.SUCCESS;
        }
        if (synchronizedItemCount == 0) {
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
                                int synchronizedCount, int failureCount, int identityRejectedCount,
                                LocalDateTime startedAt,
                                LocalDateTime finishedAt) {
        Optional<LocalDateTime> latestDataTime = contents.stream()
                .map(content -> content.result().dataTime()).max(LocalDateTime::compareTo);
        String fallback = contents.stream().anyMatch(content -> content.result().degraded())
                ? "DEGRADED" : "NONE";
        long elapsedMillis = java.time.Duration.between(startedAt, finishedAt).toMillis();
        return "outcome=" + batch.outcome() + ";elapsedMs=" + elapsedMillis + ";dataTime="
                + latestDataTime.map(LocalDateTime::toString).orElse("NONE") + ";quality=accepted:"
                + synchronizedCount + ",rejected:" + failureCount + ";identityRejected="
                + identityRejectedCount + ";rejectionReason="
                + (identityRejectedCount == 0 ? "NONE" : "IDENTITY_REVIEW_REQUIRED") + ";fallback=" + fallback;
    }
}
