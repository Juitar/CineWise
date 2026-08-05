package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.ErrorCode;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * 单版本快照过期仍保留为最近成功的真实资料；是否回退到上一版本由后续两版本快照实现决定。</p>
 *
 * <p>Demo 是最后一层，而不是实时数据的替代品。它只能提供版本化影片和影院资料，不能让用户误以为
 * 系统获得了新的排期或余票。没有任何可用内容时，调用方得到固定的 303004 错误。</p>
 */
@Service
public class ContentQueryService implements ContentPurchaseQueryPort {

    private static final String CHANGSHA_CITY_CODE = "430100";
    private static final int DEMO_PURCHASE_MOVIE_LIMIT = 3;

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
     * 依次读取当前真实缓存、当前真实快照和 Demo；全部缺失时返回 303004。
     *
     * <p>参数校验由 ContentQuery 构造时完成，因此进入此方法后不会对缓存、数据库或 Demo 产生无效访问。</p>
     */
    public ContentResult<List<? extends ContentItem>> query(ContentQuery query) {
        return cachePort.find(query).filter(this::isVerifiedLiveContent).map(this::asCurrentVersion)
                .orElseGet(() -> findFromSnapshot(query)
                // Demo 是离线最后回退层，绝不能写回 Redis 后被下一次查询伪装成真实缓存。
                .orElseGet(() -> demoProvider.query(query)
                        .orElseThrow(() -> new BusinessException(ContentErrorCode.DATA_UNAVAILABLE))));
    }

    /**
     * 一次读取影片目录后按内部 ID 过滤，避免 Redis 不可用时为每部影片分别等待连接超时。
     * 单项缺失只排除该项，不拖垮其他有效影片；输入最多来自未来七天排期聚合。
     */
    @Override
    public Map<Long, MovieSummary> findMovieSummaries(Set<Long> movieIds) {
        if (movieIds.isEmpty()) {
            return Map.of();
        }
        if (movieIds.size() > 100) {
            throw new IllegalArgumentException("movieIds must not contain more than 100 items");
        }
        try {
            ContentResult<List<? extends ContentItem>> result = query(
                    new ContentQuery(com.miaoyu.ticket.content.domain.ContentResourceType.MOVIE,
                            null, null, null));
            Map<Long, MovieSummary> summaries = new LinkedHashMap<>();
            result.data().stream()
                    .map(MovieContent.class::cast)
                    .filter(movie -> movie.movieId() != null && movieIds.contains(movie.movieId()))
                    .sorted(java.util.Comparator.comparingLong(MovieContent::movieId))
                    .forEach(movie -> summaries.put(movie.movieId(),
                            new MovieSummary(movie.movieId(), movie.title(), movie.posterUrl())));
            return Map.copyOf(summaries);
        } catch (BusinessException exception) {
            return Map.of();
        }
    }

    /**
     * 演示购票只选择已经同步并标记为 LIVE 的长沙内容；排序使用内部 ID，保证同一数据库重复启动稳定。
     * 返回的目录只承载公开 ID 和影片时长，票价、影厅、场次与座位仍由 A 的演示种子生成。
     */
    @Override
    public Optional<ContentSeedCatalog> findChangshaLivePurchaseCatalog() {
        ContentResult<List<? extends ContentItem>> cinemaResult = query(
                new ContentQuery(com.miaoyu.ticket.content.domain.ContentResourceType.CINEMA,
                        null, CHANGSHA_CITY_CODE, null));
        ContentResult<List<? extends ContentItem>> movieResult = query(
                new ContentQuery(com.miaoyu.ticket.content.domain.ContentResourceType.MOVIE,
                        null, null, null));
        if (cinemaResult.source().type() != ContentSourceType.LIVE
                || movieResult.source().type() != ContentSourceType.LIVE) {
            return Optional.empty();
        }

        List<ContentSeedCatalog.CinemaRef> cinemas = cinemaResult.data().stream()
                .map(CinemaContent.class::cast)
                .filter(cinema -> cinema.cinemaId() != null && cinema.cinemaId() > 0)
                .sorted(java.util.Comparator.comparingLong(CinemaContent::cinemaId))
                .limit(1)
                .map(cinema -> new ContentSeedCatalog.CinemaRef(cinema.cinemaId(), cinema.sourceCinemaId()))
                .toList();
        List<ContentSeedCatalog.MovieRef> movies = movieResult.data().stream()
                .map(MovieContent.class::cast)
                .filter(movie -> movie.movieId() != null && movie.movieId() > 0)
                .sorted(java.util.Comparator.comparingLong(MovieContent::movieId))
                .limit(DEMO_PURCHASE_MOVIE_LIMIT)
                .map(movie -> new ContentSeedCatalog.MovieRef(
                        movie.movieId(), movie.sourceMovieId(), movie.durationMinutes()))
                .toList();
        return cinemas.isEmpty() || movies.isEmpty()
                ? Optional.empty()
                : Optional.of(new ContentSeedCatalog(movies, cinemas));
    }

    private java.util.Optional<ContentResult<List<? extends ContentItem>>> findFromSnapshot(ContentQuery query) {
        // 当前快照即使过期也仍是最近成功的真实资料，不能因为拉取失败而被 Demo 覆盖。
        // 两版本迁移完成前这里只有“当前版本”；不能伪造上一版本并标为 SNAPSHOT。
        return snapshotPort.findLatest(query).flatMap(result -> {
            // 历史版本可能在本规则前保存了 Demo 快照。它只能由最后一层直接读取，不能升级成真实资料。
            if (!isVerifiedLiveContent(result)) {
                return java.util.Optional.empty();
            }
            ContentResult<List<? extends ContentItem>> usable = asCurrentVersion(result);
            if (!usable.expired()) {
                cachePort.save(query, usable);
            }
            return java.util.Optional.of(usable);
        });
    }

    /**
     * 缓存和快照的优先级只属于通过 Provider 校验后留下的真实基础资料。
     *
     * <p>不能只根据 Redis 命中判断数据层级：旧版本可能已把 Demo 写入同一个键。拒绝非 LIVE 来源后，
     * 固定目录仍会在最后一层按 MOCK 返回，页面不会把演示内容显示为缓存或历史真实数据。</p>
     */
    private boolean isVerifiedLiveContent(ContentResult<List<? extends ContentItem>> result) {
        return result.source().type() == ContentSourceType.LIVE;
    }

    /**
     * 当前真实版本按业务时钟重新计算时效，保留来源和同步时间。
     *
     * <p>这里不延长 expiresAt，也不将 source 改成实时来源，避免数据在展示层丢失其陈旧性。</p>
     */
    private ContentResult<List<? extends ContentItem>> asCurrentVersion(
            ContentResult<List<? extends ContentItem>> result) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        boolean expired = result.expiresAt().isBefore(now);
        return new ContentResult<>(result.data(), result.source(), result.dataTime(), result.expiresAt(), expired,
                // isExpired 只提示资料时间；当前版本不因过期自动变成 SNAPSHOT 降级。
                false, null);
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
