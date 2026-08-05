package com.miaoyu.ticket.content.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentQueryService;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.domain.CinemaContent;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 页面使用的影片、影院公开只读接口。
 *
 * <p>Controller 只负责 HTTP 参数、DTO 映射与稳定错误码。内容来源、快照回退和过期判断仍由
 * {@link ContentQueryService} 决定，因此这里不会访问内容表，也不会读取 A 的场次、座位或票价。</p>
 *
 * <p>列表和详情都使用同一套来源、时效字段，页面可以明确区分 Demo、缓存和快照结果，不能把降级数据
 * 当作实时票务事实。</p>
 *
 * <p>该接口不接收用户身份、当前位置或路线参数。影片和影院是公开静态内容，个性化、距离和路线结论
 * 必须由各自模块在明确授权后单独提供。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class ContentController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_FILTER_LENGTH = 100;

    private final ContentQueryService contentQueryService;
    private final ObjectMapper objectMapper;

    /** 内容查询只能通过 Application Service，避免 Web 层绕过 D 模块回退规则。 */
    public ContentController(ContentQueryService contentQueryService, ObjectMapper objectMapper) {
        this.contentQueryService = contentQueryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询影片列表。来源字段放在分页对象中，避免每条 Demo 记录重复同一份时效说明。
     *
     * <p>空 keyword 表示浏览全部影片，不等同于查询所有外部 Provider 数据；本期只会读取已经确定的
     * Demo 内容和既有回退结果。</p>
     *
     * <p>genre 使用精确匹配，防止“科幻”被误当作“科幻惊悚”等不同分类。排序在接口层固定为业务 ID
     * 升序，避免页面因底层集合顺序变化而闪动。</p>
     */
    @GetMapping("/movies")
    @Operation(summary = "查询影片列表")
    public Result<ContentPageResponse<MovieResponse>> listMovies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        String normalizedKeyword = normalizeFilter(keyword, "keyword");
        String normalizedGenre = normalizeFilter(genre, "genre");

        ContentResult<List<? extends ContentItem>> result = contentQueryService.query(
                new ContentQuery(ContentResourceType.MOVIE, null, null, normalizedKeyword));
        List<MovieResponse> records = result.data().stream()
                .map(MovieContent.class::cast)
                .map(this::toMovieResponse)
                .filter(movie -> titleContains(movie.title(), normalizedKeyword))
                .filter(movie -> normalizedGenre == null || movie.genres().contains(normalizedGenre))
                .sorted(Comparator.comparingLong(movie -> Long.parseLong(movie.movieId())))
                .toList();
        return Result.success(toPage(records, page, size, result));
    }

    /**
     * 查询影片详情。
     *
     * <p>详情查询只接受数据库业务 ID，不接受 JSON 中的来源 ID；来源 ID 是 Demo 目录内部定位键，不能被
     * 页面缓存或链接当作稳定业务标识。</p>
     *
     * <p>返回的来源字段由本次查询结果决定，不能复用列表页上一次的时效，避免页面在缓存过期后继续显示
     * 旧的 Demo 标识。</p>
     */
    @GetMapping("/movies/{movieId}")
    @Operation(summary = "查询影片详情")
    public Result<MovieDetailResponse> getMovie(
            @Parameter(in = ParameterIn.PATH, required = true, schema = @Schema(pattern = "[1-9][0-9]*"))
            @PathVariable String movieId) {
        ContentResult<List<? extends ContentItem>> result = queryDetail(ContentResourceType.MOVIE, movieId);
        MovieContent movie = MovieContent.class.cast(result.data().getFirst());
        return Result.success(toMovieDetailResponse(movie, result));
    }

    /**
     * 查询指定城市的影院。location 始终表示 cityCode，不能被前端当作坐标或距离条件。
     *
     * <p>影院列表强制要求城市代码，避免在没有用户位置授权时返回跨城市的全量数据。关键词只针对
     * 可展示的名称、行政区和地址，不能把内部 source ID 当作页面搜索条件。</p>
     *
     * <p>接口不会基于地址推算距离或推荐路线；这些结果依赖用户当前位置，属于出行模块的单次请求数据。</p>
     */
    @GetMapping("/cinemas")
    @Operation(summary = "查询影院列表")
    public Result<ContentPageResponse<CinemaResponse>> listCinemas(
            @RequestParam String location,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        String normalizedLocation = normalizeFilter(location, "location");
        String normalizedKeyword = normalizeFilter(keyword, "keyword");
        if (normalizedLocation == null) {
            throw invalidParameter("location 不能为空");
        }

        ContentResult<List<? extends ContentItem>> result = contentQueryService.query(
                new ContentQuery(ContentResourceType.CINEMA, null, normalizedLocation, normalizedKeyword));
        List<CinemaResponse> records = result.data().stream()
                .map(CinemaContent.class::cast)
                .map(this::toCinemaResponse)
                .sorted(Comparator.comparingLong(cinema -> Long.parseLong(cinema.cinemaId())))
                .toList();
        return Result.success(toPage(records, page, size, result));
    }

    /**
     * 查询影院详情。
     *
     * <p>接口不暴露坐标、直线距离或路线时间，避免页面把静态内容误作路线事实。若用户需要路线，必须
     * 由出行模块在用户主动发起后处理，不复用这里的公开内容响应。</p>
     */
    @GetMapping("/cinemas/{cinemaId}")
    @Operation(summary = "查询影院详情")
    public Result<CinemaDetailResponse> getCinema(
            @Parameter(in = ParameterIn.PATH, required = true, schema = @Schema(pattern = "[1-9][0-9]*"))
            @PathVariable String cinemaId) {
        ContentResult<List<? extends ContentItem>> result = queryDetail(ContentResourceType.CINEMA, cinemaId);
        CinemaContent cinema = CinemaContent.class.cast(result.data().getFirst());
        return Result.success(toCinemaDetailResponse(cinema, result));
    }

    /** 按 ID 查询时，内部“全部来源不可用”也要对页面表现为不可展示的 404。 */
    private ContentResult<List<? extends ContentItem>> queryDetail(ContentResourceType resourceType, String rawId) {
        // 详情先验证字符串业务 ID，不能让 Long 的溢出异常变成 500 或查询到错误记录。
        long contentId = parsePositiveId(rawId);
        try {
            // Application Service 负责缓存、快照和 Demo 回退；HTTP 层不能绕过它直接读 JDBC。
            ContentResult<List<? extends ContentItem>> result = contentQueryService.query(
                    new ContentQuery(resourceType, contentId, null, null));
            if (result.data().isEmpty()) {
                // 数据源返回空列表时与删除内容对页面的效果相同，统一为稳定的“未找到”。
                throw contentNotFound();
            }
            return result;
        } catch (BusinessException exception) {
            if (exception.getErrorCode().code() == 303004) {
                // Provider、快照和 Demo 都无结果时不能泄漏内部降级编号，页面只需知道内容不可展示。
                throw contentNotFound();
            }
            throw exception;
        }
    }

    private long parsePositiveId(String rawId) {
        // API 业务 ID 始终使用十进制字符串，拒绝负数、小数和科学计数法以避免跨端精度语义不一致。
        if (rawId == null || !rawId.matches("[1-9][0-9]*")) {
            throw invalidParameter("业务 ID 必须是正整数格式的十进制字符串");
        }
        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException exception) {
            // 正则只能保证外形，超出 Long 范围仍必须按客户端参数错误处理。
            throw invalidParameter("业务 ID 超出可查询范围");
        }
    }

    private void validatePage(int page, int size) {
        // page 从 1 开始；限制 size 是为了防止公开接口被一次请求拖垮，也让 C 的分页状态可预测。
        if (page < DEFAULT_PAGE || size < DEFAULT_PAGE || size > MAX_PAGE_SIZE) {
            throw invalidParameter("page 必须大于等于 1，size 必须在 1 到 50 之间");
        }
    }

    private String normalizeFilter(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        // 长度限制在进入 Application 层前执行，避免无界输入参与缓存键、日志或 Provider 查询。
        if (normalized.length() > MAX_FILTER_LENGTH) {
            throw invalidParameter(fieldName + " 最长 100 个字符");
        }
        // 空白筛选与未传筛选语义一致，不能把空字符串作为不同的缓存或分页条件。
        return normalized.isEmpty() ? null : normalized;
    }

    private MovieResponse toMovieResponse(MovieContent movie) {
        // 海报和上映资料来自已标准化的内容模型；列表不返回长简介，避免首屏加载不必要文本。
        // 这里不重新校验 URL：Provider/领域模型已完成 HTTPS 校验，Controller 只做公开 DTO 映射。
        return new MovieResponse(
                toPublicId(movie.movieId()),
                movie.title(),
                movie.posterUrl(),
                parseGenres(movie.genresJson()),
                movie.durationMinutes(),
                movie.rating(),
                movie.releaseDate(),
                movie.releaseStatus());
    }

    /**
     * REST 的影片关键字只查询标题。
     *
     * <p>不能把 Provider 内部来源 ID 当作页面可见搜索条件，否则前端会依赖可替换的 Demo 目录身份，
     * 真实 Provider 接入后无法保持查询含义。</p>
     */
    private boolean titleContains(String title, String keyword) {
        return keyword == null || title.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private MovieDetailResponse toMovieDetailResponse(MovieContent movie, ContentResult<?> result) {
        // 详情把时效字段下沉到记录本身，页面刷新详情时无需再拼接列表包装中的来源信息。
        // summary 为空表示来源未给短简介，不能把评分、影评或场次说明替换进去。
        ContentMeta meta = toMeta(result);
        return new MovieDetailResponse(
                toPublicId(movie.movieId()),
                movie.title(),
                movie.posterUrl(),
                movie.summary(),
                parseGenres(movie.genresJson()),
                movie.durationMinutes(),
                movie.rating(),
                movie.releaseDate(),
                movie.releaseStatus(),
                meta.source(),
                meta.sourceType(),
                meta.dataTime(),
                meta.expiresAt(),
                meta.isExpired(),
                meta.degraded(),
                meta.fallbackType());
    }

    private CinemaResponse toCinemaResponse(CinemaContent cinema) {
        // 列表只返回影院静态展示字段，坐标留在内部内容模型中，避免公开接口暴露精确位置。
        // cityCode 保留为标准行政编码，不能被前端替换为用户输入的自由文本位置。
        // address 只用于显示和关键词匹配，不触发任何地理编码或路线服务调用。
        // 影院的票价、场次和余座均属于 A 的票务查询，不能在此 DTO 中补充。
        return new CinemaResponse(
                toPublicId(cinema.cinemaId()),
                cinema.name(),
                cinema.cityCode(),
                cinema.area(),
                cinema.address());
    }

    private CinemaDetailResponse toCinemaDetailResponse(CinemaContent cinema, ContentResult<?> result) {
        // 详情仍不计算距离和路线时间；没有本次用户位置时这些字段没有可信含义。
        ContentMeta meta = toMeta(result);
        return new CinemaDetailResponse(
                toPublicId(cinema.cinemaId()),
                cinema.name(),
                cinema.cityCode(),
                cinema.area(),
                cinema.address(),
                meta.source(),
                meta.sourceType(),
                meta.dataTime(),
                meta.expiresAt(),
                meta.isExpired(),
                meta.degraded(),
                meta.fallbackType());
    }

    /** genres_json 是持久化内部表示；REST 必须始终输出数组，损坏值按空数组展示。 */
    private List<String> parseGenres(String genresJson) {
        try {
            // JSON 数组是持久化细节，REST 固定输出数组，避免前端同时处理字符串和数组两种格式。
            return objectMapper.readValue(genresJson, new TypeReference<>() { });
        } catch (Exception exception) {
            // 单条 Demo 分类损坏不能使整个公开列表失败；空数组比回显原始损坏 JSON 更安全。
            return List.of();
        }
    }

    private String toPublicId(Long contentId) {
        // Demo 目录只有在映射到真实数据库业务 ID 后才能用于公开响应，不能把 source ID 暴露给调用方。
        if (contentId == null || contentId <= 0) {
            throw contentNotFound();
        }
        return Long.toString(contentId);
    }

    /**
     * 将内部查询结果转换为公开来源元数据。
     *
     * <p>API 层把内部 expired 映射为 isExpired，不能泄漏内部字段名。fallbackType 在没有降级来源时必须
     * 保持 null，页面不能根据空字符串误判为某一种已知回退层。</p>
     *
     * <p>sourceType 保持数据来源类型而非数据库表名，使后续真实 Provider 接入时不需要改变前端展示逻辑。</p>
     */
    private ContentMeta toMeta(ContentResult<?> result) {
        // 内部 expired 的命名不作为 API 契约，统一映射为 isExpired 以保持页面 DTO 与 OpenAPI 一致。
        return new ContentMeta(
                result.source().name(),
                result.source().type().name(),
                toOffsetDateTime(result.dataTime()),
                toOffsetDateTime(result.expiresAt()),
                result.expired(),
                result.degraded(),
                result.fallbackType() == null ? null : result.fallbackType().name());
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        // 内容内部时间没有时区；对外固定为业务时区偏移，避免浏览器按本机时区误读 Demo 有效期。
        return value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }

    private <T> ContentPageResponse<T> toPage(
            List<T> allRecords, int page, int size, ContentResult<?> result) {
        // 超出末页返回空 records 而不是 404，前端刷新或删除后可安全保持当前分页状态。
        // fromIndex 先截断到总数，避免 page 很大时出现越界异常。
        // toIndex 只覆盖当前页窗口，不会把未请求记录序列化到响应中。
        // total 始终使用筛选后的完整集合，让前端分页控件能够计算最后一页。
        // 来源元数据与当前页绑定，不能由调用方从另一页的缓存响应中复用。
        int fromIndex = Math.min((page - DEFAULT_PAGE) * size, allRecords.size());
        int toIndex = Math.min(fromIndex + size, allRecords.size());
        ContentMeta meta = toMeta(result);
        return new ContentPageResponse<>(
                allRecords.subList(fromIndex, toIndex),
                allRecords.size(),
                page,
                size,
                meta.source(),
                meta.sourceType(),
                meta.dataTime(),
                meta.expiresAt(),
                meta.isExpired(),
                meta.degraded(),
                meta.fallbackType());
    }

    private BusinessException invalidParameter(String message) {
        // 统一使用公共参数错误码，C 不需要依赖 Controller 内部异常文本判断表单状态。
        // message 仅用于开发定位，页面分支应以稳定数值 code 为准。
        return new BusinessException(CommonErrorCode.INVALID_PARAMETER, message);
    }

    private BusinessException contentNotFound() {
        // 不区分删除、不存在和无可展示回退数据，避免暴露内容内部生命周期和 Provider 状态。
        // 统一响应也防止调用方通过状态差异枚举已删除内容。
        return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "内容不存在或不可展示");
    }

    /**
     * 影片列表记录。
     *
     * <p>Demo 暂无海报时明确返回 null，页面自行使用默认海报；不能用空字符串代替，因为空字符串会被
     * 浏览器当作一个可请求地址。</p>
     *
     * <p>genres 固定为数组，即使数据源没有分类也返回空数组，避免前端为同一字段维护多种类型。</p>
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MovieResponse(
            String movieId,
            String title,
            String posterUrl,
            List<String> genres,
            Integer durationMinutes,
            BigDecimal rating,
            String releaseDate,
            String releaseStatus) {
    }

    /**
     * 影片详情记录。
     *
     * <p>简介和海报字段为后续真实 Provider 预留，本期固定为 null；预留字段不代表已有外部内容。</p>
     *
     * <p>来源和有效期跟随详情返回，调用方可以在不加载列表的情况下识别当前是否为演示或降级数据。</p>
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MovieDetailResponse(
            String movieId,
            String title,
            String posterUrl,
            String summary,
            List<String> genres,
            Integer durationMinutes,
            BigDecimal rating,
            String releaseDate,
            String releaseStatus,
            String source,
            String sourceType,
            OffsetDateTime dataTime,
            OffsetDateTime expiresAt,
            boolean isExpired,
            boolean degraded,
            String fallbackType) {
    }

    /**
     * 影院列表记录。
     *
     * <p>列表不带路线和坐标，因为它们依赖用户当前位置和路线服务。公开地址仅用于文字展示和关键词匹配，
     * 不用于后台推断用户位置。</p>
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CinemaResponse(
            String cinemaId,
            String name,
            String cityCode,
            String area,
            String address) {
    }

    /**
     * 影院详情记录。
     *
     * <p>详情只描述影院静态内容。来源字段帮助前端展示 Demo 标识，但不允许据此推断库存、场次或交通
     * 结论。</p>
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CinemaDetailResponse(
            String cinemaId,
            String name,
            String cityCode,
            String area,
            String address,
            String source,
            String sourceType,
            OffsetDateTime dataTime,
            OffsetDateTime expiresAt,
            boolean isExpired,
            boolean degraded,
            String fallbackType) {
    }

    /**
     * 分页来源包装。
     *
     * <p>records 只包含页面渲染字段，来源和时效集中在分页对象中，避免同一页的每条记录出现相互矛盾的
     * 降级状态。</p>
     *
     * <p>total 是筛选后的总数，不是目录总条目数；页面据此计算页码时不会把其他城市或不匹配类型计入。</p>
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ContentPageResponse<T>(
            List<T> records,
            long total,
            int page,
            int size,
            String source,
            String sourceType,
            OffsetDateTime dataTime,
            OffsetDateTime expiresAt,
            boolean isExpired,
            boolean degraded,
            String fallbackType) {
    }

    /**
     * 内部来源字段映射值。
     *
     * <p>分页和详情共用这个值，防止接口演进时一端遗漏 isExpired、fallbackType 等字段，导致 C 对同一
     * 内容得出不同的展示结论。</p>
     */
    private record ContentMeta(
            String source,
            String sourceType,
            OffsetDateTime dataTime,
            OffsetDateTime expiresAt,
            boolean isExpired,
            boolean degraded,
            String fallbackType) {
    }
}
