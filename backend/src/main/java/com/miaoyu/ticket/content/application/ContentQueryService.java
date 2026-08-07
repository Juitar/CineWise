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
    private final ContentLocalMovieCatalogPort localMovieCatalogPort;

    /** 端口均在 Application 边界注入，查询服务不接触 Redis、JDBC 或 JSON 实现。 */
    /** 缓存、快照和 Demo 的先后关系只在本服务维护，调用方不能跳过其中任意一层。 */
    /** Clock 是唯一的过期判断来源，测试与生产环境不会因系统默认时区不同而产生不同结果。 */
    /** Properties 只承载时间窗口，不承载任何 Provider 地址或用户输入，避免配置扩大模块职责。 */
    @org.springframework.beans.factory.annotation.Autowired
    public ContentQueryService(ContentCachePort cachePort, ContentSnapshotPort snapshotPort,
                               ContentProvider demoProvider, ContentProperties properties, Clock clock,
                               ContentLocalMovieCatalogPort localMovieCatalogPort) {
        this.cachePort = cachePort;
        this.snapshotPort = snapshotPort;
        this.demoProvider = demoProvider;
        this.properties = properties;
        this.clock = clock;
        this.localMovieCatalogPort = localMovieCatalogPort;
    }

    /** 单元测试夹具没有真实数据库目录时仍可使用原有缓存/快照/Demo 查询。 */
    public ContentQueryService(ContentCachePort cachePort, ContentSnapshotPort snapshotPort,
                               ContentProvider demoProvider, ContentProperties properties, Clock clock) {
        this.cachePort = cachePort;
        this.snapshotPort = snapshotPort;
        this.demoProvider = demoProvider;
        this.properties = properties;
        this.clock = clock;
        this.localMovieCatalogPort = null;
    }

    /** 影片列表优先读取本地完整目录；本地端口缺失仅用于旧单元测试夹具兼容。 */
    public Optional<ContentResult<List<? extends ContentItem>>> queryLocalMovies(
            String keyword, String releaseStatus) {
        return localMovieCatalogPort == null
                ? Optional.empty()
                : localMovieCatalogPort.findMovies(keyword, releaseStatus);
    }

    /**
     * 依次读取当前真实缓存、当前真实快照和 Demo；全部缺失时返回 303004。
     *
     * <p>参数校验由 ContentQuery 构造时完成，因此进入此方法后不会对缓存、数据库或 Demo 产生无效访问。</p>
     */
    public ContentResult<List<? extends ContentItem>> query(ContentQuery query) {
        ContentQuery storedQuery = cinemaDirectoryQuery(query);
        ContentResult<List<? extends ContentItem>> result = cachePort.find(storedQuery)
                .filter(this::isVerifiedLiveContent).map(this::asCurrentVersion)
                .orElseGet(() -> findFromSnapshot(storedQuery)
                        // Demo 是离线最后回退层，绝不能写回 Redis 后被下一次查询伪装成真实缓存。
                        .orElseGet(() -> demoProvider.query(storedQuery)
                                .orElseThrow(() -> new BusinessException(ContentErrorCode.DATA_UNAVAILABLE))));
        return filterCinemaDirectory(query, result);
    }

    /**
     * 影院同步按城市保存完整目录，关键字仅是页面上的本地筛选条件。
     * 若把关键字放进快照键，用户每输入一个词都会错过同一城市的真实资料并落回 Demo。
     *
     * <p>这里不改变 API 参数：Controller 仍接收 {@code location} 和 {@code keyword}。其中 location 是用户
     * 选择的城市编码，决定读取哪一份目录；keyword 只影响已经读出的列表。两者不能混为同一个存储条件。</p>
     *
     * <p>同步任务每个城市只写入一份无关键字的影院目录。这样万达、天心、岳麓等不同输入都复用同一份
     * 已校验的本地数据，分页和排序也始终针对同一批影院执行。</p>
     *
     * <p>本方法只适用于影院列表：按影院 ID 查询时必须保留 ID；影片查询仍沿用各自已有的查询键，不能被
     * 这个兼容规则意外改写。</p>
     *
     * <p>页面查询路径不会调用真实 Provider。若本地没有该城市目录，才会读取固定 Demo 目录；Provider 的
     * 网络请求仅能由同步任务发起。</p>
     */
    private ContentQuery cinemaDirectoryQuery(ContentQuery query) {
        return query.resourceType() == com.miaoyu.ticket.content.domain.ContentResourceType.CINEMA
                && query.contentId() == null && query.cityCode() != null && query.keyword() != null
                ? new ContentQuery(query.resourceType(), null, query.cityCode(), null)
                : query;
    }

    /**
     * 真实城市目录存在时，即使关键词没有命中也必须保留真实来源的空结果。
     * 因此筛选放在完成缓存、快照或 Demo 选择之后，绝不能把空筛选结果再次作为回退条件。
     *
     * <p>返回空列表表示“这个城市的已同步目录中没有匹配项”，不是“本地资料失效”。此时继续回退 Demo
     * 会把两个不同来源的城市目录混在一次查询里，也会让用户误以为搜索到了真实影院。</p>
     *
     * <p>筛选字段限制为影院名称、行政区和地址，都是影院基础资料。这里不引入场次、价格、座位或距离，
     * 这些数据分别属于排期和票务模块，不能由内容目录猜测。</p>
     *
     * <p>使用 {@link java.util.Locale#ROOT} 做大小写转换，避免服务器默认语言环境影响英文或数字混合名称
     * 的匹配结果。中文输入不会受此转换影响。</p>
     *
     * <p>Controller 在本方法之后执行现有分页。因此页码和总数反映的是筛选后的本地结果，而不是完整目录
     * 的页码，用户不会因关键词筛选拿到空白的错误页。</p>
     */
    private ContentResult<List<? extends ContentItem>> filterCinemaDirectory(
            ContentQuery requestedQuery, ContentResult<List<? extends ContentItem>> result) {
        if (requestedQuery.resourceType() != com.miaoyu.ticket.content.domain.ContentResourceType.CINEMA
                || requestedQuery.keyword() == null) {
            return result;
        }
        String keyword = requestedQuery.keyword().toLowerCase(java.util.Locale.ROOT);
        List<? extends ContentItem> filtered = result.data().stream().map(CinemaContent.class::cast)
                .filter(cinema -> matchesCinemaKeyword(cinema, keyword)).toList();
        return new ContentResult<>(filtered, result.source(), result.dataTime(), result.expiresAt(), result.expired(),
                result.degraded(), result.fallbackType());
    }

    /**
     * 只判断可展示的影院文本字段，确保搜索结果能直接向用户说明命中位置。
     *
     * <p>字段在 {@link CinemaContent} 创建时已由 Provider 或快照转换层校验为非空；本处不补造空字符串，
     * 以免坏数据被静默当作“不匹配”而掩盖同步问题。</p>
     *
     * <p>搜索不区分大小写，使用包含匹配而非全词匹配，符合用户输入简称或商圈名称时的预期。</p>
     *
     * <p>本方法不修改目录记录，也不把筛选结果写回缓存或快照，避免一次用户搜索污染完整城市目录。</p>
     *
     * <p>缓存中始终保存未筛选目录，后续不同关键词才能复用同一份同步结果。</p>
     * <p>这也避免把一个用户的搜索条件当成全城市的长期数据。</p>
     */
    private boolean matchesCinemaKeyword(CinemaContent cinema, String keyword) {
        return cinema.name().toLowerCase(java.util.Locale.ROOT).contains(keyword)
                || cinema.area().toLowerCase(java.util.Locale.ROOT).contains(keyword)
                || cinema.address().toLowerCase(java.util.Locale.ROOT).contains(keyword);
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
        ContentResult<List<? extends ContentItem>> result = query(
                new ContentQuery(com.miaoyu.ticket.content.domain.ContentResourceType.MOVIE,
                        null, null, null));
        Map<Long, MovieSummary> summaries = new LinkedHashMap<>();
        result.data().stream()
                .map(MovieContent.class::cast)
                .filter(movie -> movie.movieId() != null && movieIds.contains(movie.movieId()))
                .sorted(java.util.Comparator.comparingLong(MovieContent::movieId))
                .forEach(movie -> summaries.put(movie.movieId(), new MovieSummary(
                        movie.movieId(),
                        movie.title(),
                        movie.posterUrl(),
                        result.source().name(),
                        result.dataTime())));
        return Map.copyOf(summaries);
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
