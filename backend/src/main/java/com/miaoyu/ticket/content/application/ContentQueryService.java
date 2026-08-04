package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.ErrorCode;
import com.miaoyu.ticket.content.domain.ContentFallbackType;
import com.miaoyu.ticket.content.domain.ContentItem;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 内容查询的固定回退顺序。
 *
 * <p>缓存和快照都不可用时才读取 Demo，避免演示数据掩盖最近一次有效内容；所有分支只返回内容资料，
 * 不会推断场次、价格、座位或库存。</p>
 *
 * <p>本服务不负责从网络抓取内容。真实 Provider 接入后只需在更高层写入标准化快照，不得改变这里的
 * 回退顺序。这样外部网络波动不会改变页面和推荐对“数据来自哪里”的判断。</p>
 *
 * <p>缓存命中也必须重新检查内容有效期，因为 Redis 的 TTL 是加速手段，不是内容真实性的判断依据。
 * 快照过期后只有处于最大陈旧期内才可返回，且返回值必须明确标记为过期。</p>
 *
 * <p>Demo 是最后一层，而不是实时数据的替代品。它只能提供版本化影片和影院资料，不能让用户误以为
 * 系统获得了新的排期或余票。没有任何可用内容时，调用方得到固定的 303004 错误。</p>
 */
@Service
public class ContentQueryService {

    private final ContentCachePort cachePort;
    private final ContentSnapshotPort snapshotPort;
    private final ContentProvider demoProvider;
    private final ContentProperties properties;
    private final Clock clock;

    /** 端口均在 Application 边界注入，查询服务不接触 Redis、JDBC 或 JSON 实现。 */
    /** 缓存、快照和 Demo 的先后关系只在本服务维护，调用方不能跳过其中任意一层。 */
    /** Clock 是唯一的过期判断来源，测试与生产环境不会因系统默认时区不同而产生不同结果。 */
    /** Properties 只承载时间窗口，不承载任何 Provider 地址或用户输入，避免配置扩大模块职责。 */
    public ContentQueryService(ContentCachePort cachePort, ContentSnapshotPort snapshotPort,
                               ContentProvider demoProvider, ContentProperties properties, Clock clock) {
        this.cachePort = cachePort;
        this.snapshotPort = snapshotPort;
        this.demoProvider = demoProvider;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 依次读取有效缓存、快照、允许的旧快照和 Demo；全部缺失时返回 303004。
     *
     * <p>参数校验由 ContentQuery 构造时完成，因此进入此方法后不会对缓存、数据库或 Demo 产生无效访问。</p>
     */
    public ContentResult<List<? extends ContentItem>> query(ContentQuery query) {
        return cachePort.find(query).orElseGet(() -> findFromSnapshot(query)
                // Demo 是离线最后回退层，绝不能写回 Redis 后被下一次查询伪装成真实缓存。
                .orElseGet(() -> demoProvider.query(query)
                        .orElseThrow(() -> new BusinessException(ContentErrorCode.DATA_UNAVAILABLE))));
    }

    private java.util.Optional<ContentResult<List<? extends ContentItem>>> findFromSnapshot(ContentQuery query) {
        // 有效快照重新写入缓存，减少下一次相同查询的数据库读取。
        // 超过最大陈旧期的快照不返回，避免历史内容长期停留在用户页面。
        // 允许陈旧的边界采用闭区间，刚好到期的快照仍按只读信息处理。
        return snapshotPort.findLatest(query).flatMap(result -> {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
            if (!result.expiresAt().isBefore(now)) {
                ContentResult<List<? extends ContentItem>> usable = withExpiration(result, false);
                cachePort.save(query, usable);
                return java.util.Optional.of(usable);
            }
            if (!result.expiresAt().plus(properties.maxStale()).isBefore(now)) {
                return java.util.Optional.of(withExpiration(result, true));
            }
            return java.util.Optional.empty();
        });
    }

    /**
     * 过期快照仅改为只读标识，保留其原始来源和时间，供推荐模块明确排除。
     *
     * <p>这里不延长 expiresAt，也不将 source 改成实时来源，避免数据在展示层丢失其陈旧性。</p>
     */
    private ContentResult<List<? extends ContentItem>> withExpiration(
            ContentResult<List<? extends ContentItem>> result, boolean expired) {
        return new ContentResult<>(result.data(), result.source(), result.dataTime(), result.expiresAt(), expired,
                true, ContentFallbackType.SNAPSHOT);
    }

    /** 内容模块的不可用错误码，表示缓存、快照和 Demo 都不能提供数据。 */
    private enum ContentErrorCode implements ErrorCode {
        DATA_UNAVAILABLE;

        /** 错误码与外部数据设计保持一致，调用方可据此提示稍后刷新。 */
        @Override public int code() { return 303004; }
        @Override public String message() { return "内容数据暂不可用"; }
        @Override public HttpStatus httpStatus() { return HttpStatus.SERVICE_UNAVAILABLE; }
    }
}
