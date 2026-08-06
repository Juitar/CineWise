package com.miaoyu.ticket.content.infrastructure.provider;

import com.miaoyu.ticket.content.application.ContentProperties;
import com.miaoyu.ticket.content.application.ContentIdentityLookupPort;
import com.miaoyu.ticket.content.application.ContentProvider;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.application.DemoContentCatalog;
import com.miaoyu.ticket.content.application.DemoContentCatalogProvider;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentFallbackType;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 版本化 Demo 内容的只读 Provider。
 *
 * <p>它只在内容查询没有更高优先级数据时提供 Mock 回退，因此返回结果明确标记为降级和 `MOCK`。
 * 该 Provider 不写数据库、不创建场次，也不生成票价、座位或库存。</p>
 */
@Component
public class DemoContentProvider implements ContentProvider {

    private final DemoContentCatalogProvider catalogProvider;
    private final ContentProperties properties;
    private final ContentIdentityLookupPort identityLookupPort;

    /**
     * 注入版本目录、可调有效期和业务时钟。
     *
     * <p>不直接读取系统当前时间，确保同一数据版本在演示、测试和问题复现时具有相同时间封套。</p>
     */
    public DemoContentProvider(
            DemoContentCatalogProvider catalogProvider,
            ContentProperties properties,
            ContentIdentityLookupPort identityLookupPort) {
        this.catalogProvider = catalogProvider;
        this.properties = properties;
        this.identityLookupPort = identityLookupPort;
    }

    /**
     * 按资源类型和基础查询条件筛选固定目录，并为本轮查询生成统一时间封套。
     *
     * <p>目录自身没有数据库主键。收到 contentId 时先由端口从内容表查回对应来源 ID，再精确筛选目录并
     * 将实际主键写入返回 DTO；未知 ID 直接返回空，不能退化为整份目录。</p>
     *
     * <p>返回的内容顺序完全沿用 JSON 文件顺序。这样页面演示和固定推荐夹具能复现同一候选，不依赖
     * 数据库查询计划、区域设置或流式并发顺序。</p>
     */
    @Override
    public Optional<ContentResult<List<? extends ContentItem>>> query(ContentQuery query) {
        // 目录加载失败需要由上层继续尝试快照或返回不可用，不能把半截数据伪装成完整 Demo。
        DemoContentCatalog catalog = catalogProvider.load();
        Optional<String> requestedSourceId = findRequestedSourceId(query);
        if (query.contentId() != null && requestedSourceId.isEmpty()) {
            return Optional.empty();
        }
        List<? extends ContentItem> content = query.resourceType() == ContentResourceType.MOVIE
                ? findMovies(catalog.movies(), query, requestedSourceId.orElse(null))
                : findCinemas(catalog.cinemas(), query, requestedSourceId.orElse(null));
        if (content.isEmpty() && query.contentId() != null) {
            return Optional.empty();
        }
        content = attachActualIds(query.resourceType(), content, query.contentId());
        // Demo 的检查时间属于目录版本，不能在每次浏览时伪造为“刚同步”。
        LocalDateTime dataTime = LocalDateTime.parse(catalog.checkedAt());
        ContentSource source = new ContentSource(catalog.source(), catalog.sourceType());
        return Optional.of(new ContentResult<>(
                content,
                source,
                dataTime,
                dataTime.plus(properties.demoTtl()),
                false,
                true,
                ContentFallbackType.MOCK));
    }

    private List<? extends ContentItem> attachActualIds(
            ContentResourceType type, List<? extends ContentItem> items, Long requestedId) {
        if (requestedId != null) {
            return items;
        }
        Set<String> sourceIds = items.stream()
                .map(this::sourceIdOf)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Long> ids = identityLookupPort.findContentIds(type, sourceIds);
        return items.stream().map(item -> attachActualId(item, ids)).toList();
    }

    private String sourceIdOf(ContentItem item) {
        if (item instanceof MovieContent movie) {
            return movie.sourceMovieId();
        }
        return ((CinemaContent) item).sourceCinemaId();
    }

    /** 只替换环境相关的数据库 ID，保留 JSON 目录中受 D 管理的内容字段。 */
    private ContentItem attachActualId(ContentItem item, Map<String, Long> ids) {
        if (item instanceof MovieContent movie) {
            return new MovieContent(
                    ids.get(movie.sourceMovieId()),
                    movie.sourceMovieId(),
                    movie.title(),
                    movie.genresJson(),
                    movie.durationMinutes(),
                    movie.rating());
        }
        CinemaContent cinema = (CinemaContent) item;
        return new CinemaContent(
                ids.get(cinema.sourceCinemaId()),
                cinema.sourceCinemaId(),
                cinema.name(),
                cinema.cityCode(),
                cinema.area(),
                cinema.address(),
                cinema.longitude(),
                cinema.latitude());
    }

    /**
     * 影片只按标题或来源 ID 匹配；城市不是影片本身的内容字段。
     *
     * <p>不因用户城市过滤影片，避免把“没有本地场次”错写成“影片内容不存在”；场次可购性由 A 负责。</p>
     */
    private List<MovieContent> findMovies(List<MovieContent> movies, ContentQuery query, String requestedSourceId) {
        return movies.stream()
                .filter(movie -> requestedSourceId == null || requestedSourceId.equals(movie.sourceMovieId()))
                .filter(movie -> matches(query.keyword(), movie.title(), movie.sourceMovieId()))
                .map(movie -> query.contentId() == null ? movie : new MovieContent(query.contentId(),
                        movie.sourceMovieId(), movie.title(), movie.genresJson(), movie.durationMinutes(),
                        movie.rating()))
                .toList();
    }

    /**
     * 影院同时支持城市过滤和标题、来源 ID 关键字过滤。
     *
     * <p>先按精确 cityCode 限定范围，再按关键词过滤，避免地址或行政区的模糊匹配泄露到其他城市列表。</p>
     */
    private List<CinemaContent> findCinemas(List<CinemaContent> cinemas, ContentQuery query, String requestedSourceId) {
        return cinemas.stream()
                .filter(cinema -> requestedSourceId == null || requestedSourceId.equals(cinema.sourceCinemaId()))
                .filter(cinema -> query.cityCode() == null || query.cityCode().equals(cinema.cityCode()))
                .filter(cinema -> matchesCinema(query.keyword(), cinema))
                .map(cinema -> query.contentId() == null ? cinema : new CinemaContent(query.contentId(),
                        cinema.sourceCinemaId(), cinema.name(), cinema.cityCode(), cinema.area(), cinema.address(),
                        cinema.longitude(), cinema.latitude()))
                .toList();
    }

    /** 精确 ID 只能映射到目录中已确认的来源身份，避免未知 ID 获得任意 Demo 列表。 */
    private Optional<String> findRequestedSourceId(ContentQuery query) {
        return query.contentId() == null
                ? Optional.empty()
                : identityLookupPort.findSourceId(query.resourceType(), query.contentId());
    }

    /**
     * 固定数据查询忽略大小写，保证同一关键字在不同本机区域设置下返回同样顺序。
     *
     * <p>使用 Locale.ROOT 而不是机器默认语言，避免部署机的区域设置改变英文来源 ID 的筛选结果。</p>
     */
    private boolean matches(String keyword, String title, String sourceId) {
        if (keyword == null) {
            return true;
        }
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return title.toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                || sourceId.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    /**
     * 影院 REST 只允许按名称、行政区和地址搜索，不把内部来源 ID 暴露为页面搜索条件。
     */
    private boolean matchesCinema(String keyword, CinemaContent cinema) {
        if (keyword == null) {
            return true;
        }
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return cinema.name().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                || cinema.area().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                || cinema.address().toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }
}
