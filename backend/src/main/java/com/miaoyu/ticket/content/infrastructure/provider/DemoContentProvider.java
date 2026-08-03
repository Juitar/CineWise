package com.miaoyu.ticket.content.infrastructure.provider;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.content.application.ContentProperties;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
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
    private final Clock clock;

    /**
     * 注入版本目录、可调有效期和业务时钟。
     *
     * <p>不直接读取系统当前时间，确保同一数据版本在演示、测试和问题复现时具有相同时间封套。</p>
     */
    public DemoContentProvider(
            DemoContentCatalogProvider catalogProvider,
            ContentProperties properties,
            Clock clock) {
        this.catalogProvider = catalogProvider;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 按资源类型和基础查询条件筛选固定目录，并为本轮查询生成统一时间封套。
     *
     * <p>目录自身没有数据库主键，所以这里不处理 contentId；该条件需要后续 3.5 从标准化快照或内容表
     * 查询。不能把资源数组下标或来源 ID 强行转换为本环境的 BIGINT 主键。</p>
     *
     * <p>返回的内容顺序完全沿用 JSON 文件顺序。这样页面演示和固定推荐夹具能复现同一候选，不依赖
     * 数据库查询计划、区域设置或流式并发顺序。</p>
     */
    @Override
    public Optional<ContentResult<List<? extends ContentItem>>> query(ContentQuery query) {
        // 目录加载失败需要由上层继续尝试快照或返回不可用，不能把半截数据伪装成完整 Demo。
        DemoContentCatalog catalog = catalogProvider.load();
        List<? extends ContentItem> content = query.resourceType() == ContentResourceType.MOVIE
                ? findMovies(catalog.movies(), query)
                : findCinemas(catalog.cinemas(), query);
        if (content.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime dataTime = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
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

    /**
     * 影片只按标题或来源 ID 匹配；城市不是影片本身的内容字段。
     *
     * <p>不因用户城市过滤影片，避免把“没有本地场次”错写成“影片内容不存在”；场次可购性由 A 负责。</p>
     */
    private List<MovieContent> findMovies(List<MovieContent> movies, ContentQuery query) {
        return movies.stream().filter(movie -> matches(query.keyword(), movie.title(), movie.sourceMovieId())).toList();
    }

    /**
     * 影院同时支持城市过滤和标题、来源 ID 关键字过滤。
     *
     * <p>先按精确 cityCode 限定范围，再按关键词过滤，避免地址或行政区的模糊匹配泄露到其他城市列表。</p>
     */
    private List<CinemaContent> findCinemas(List<CinemaContent> cinemas, ContentQuery query) {
        return cinemas.stream()
                .filter(cinema -> query.cityCode() == null || query.cityCode().equals(cinema.cityCode()))
                .filter(cinema -> matches(query.keyword(), cinema.name(), cinema.sourceCinemaId()))
                .toList();
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
}
