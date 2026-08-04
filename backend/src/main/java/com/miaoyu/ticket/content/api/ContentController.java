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

    /** 详情来源字段直接返回，供页面明确显示演示数据和时效。 */
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

    /** 影院详情不暴露坐标、直线距离或路线时间，避免页面把静态内容误作路线事实。 */
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
        long contentId = parsePositiveId(rawId);
        try {
            ContentResult<List<? extends ContentItem>> result = contentQueryService.query(
                    new ContentQuery(resourceType, contentId, null, null));
            if (result.data().isEmpty()) {
                throw contentNotFound();
            }
            return result;
        } catch (BusinessException exception) {
            if (exception.getErrorCode().code() == 303004) {
                throw contentNotFound();
            }
            throw exception;
        }
    }

    private long parsePositiveId(String rawId) {
        if (rawId == null || !rawId.matches("[1-9][0-9]*")) {
            throw invalidParameter("业务 ID 必须是正整数格式的十进制字符串");
        }
        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException exception) {
            throw invalidParameter("业务 ID 超出可查询范围");
        }
    }

    private void validatePage(int page, int size) {
        if (page < DEFAULT_PAGE || size < DEFAULT_PAGE || size > MAX_PAGE_SIZE) {
            throw invalidParameter("page 必须大于等于 1，size 必须在 1 到 50 之间");
        }
    }

    private String normalizeFilter(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_FILTER_LENGTH) {
            throw invalidParameter(fieldName + " 最长 100 个字符");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private MovieResponse toMovieResponse(MovieContent movie) {
        return new MovieResponse(
                toPublicId(movie.movieId()),
                movie.title(),
                null,
                parseGenres(movie.genresJson()),
                movie.durationMinutes(),
                movie.rating());
    }

    /** REST 的影片关键字只查询标题，不能把 Provider 内部来源 ID 当作页面可见搜索条件。 */
    private boolean titleContains(String title, String keyword) {
        return keyword == null || title.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private MovieDetailResponse toMovieDetailResponse(MovieContent movie, ContentResult<?> result) {
        ContentMeta meta = toMeta(result);
        return new MovieDetailResponse(
                toPublicId(movie.movieId()),
                movie.title(),
                null,
                null,
                parseGenres(movie.genresJson()),
                movie.durationMinutes(),
                movie.rating(),
                meta.source(),
                meta.sourceType(),
                meta.dataTime(),
                meta.expiresAt(),
                meta.isExpired(),
                meta.degraded(),
                meta.fallbackType());
    }

    private CinemaResponse toCinemaResponse(CinemaContent cinema) {
        return new CinemaResponse(
                toPublicId(cinema.cinemaId()),
                cinema.name(),
                cinema.cityCode(),
                cinema.area(),
                cinema.address());
    }

    private CinemaDetailResponse toCinemaDetailResponse(CinemaContent cinema, ContentResult<?> result) {
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
            return objectMapper.readValue(genresJson, new TypeReference<>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String toPublicId(Long contentId) {
        if (contentId == null || contentId <= 0) {
            throw contentNotFound();
        }
        return Long.toString(contentId);
    }

    /** API 层把内部 expired 映射为 isExpired，不能泄漏内部字段名。 */
    private ContentMeta toMeta(ContentResult<?> result) {
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
        return value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }

    private <T> ContentPageResponse<T> toPage(
            List<T> allRecords, int page, int size, ContentResult<?> result) {
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
        return new BusinessException(CommonErrorCode.INVALID_PARAMETER, message);
    }

    private BusinessException contentNotFound() {
        return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "内容不存在或不可展示");
    }

    /** 影片列表记录；Demo 暂无海报时明确返回 null，页面自行使用默认海报。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MovieResponse(
            String movieId,
            String title,
            String posterUrl,
            List<String> genres,
            Integer durationMinutes,
            BigDecimal rating) {
    }

    /** 影片详情保留简介、海报字段，后续真实 Provider 只需填值，不改接口结构。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MovieDetailResponse(
            String movieId,
            String title,
            String posterUrl,
            String summary,
            List<String> genres,
            Integer durationMinutes,
            BigDecimal rating,
            String source,
            String sourceType,
            OffsetDateTime dataTime,
            OffsetDateTime expiresAt,
            boolean isExpired,
            boolean degraded,
            String fallbackType) {
    }

    /** 影院列表不带路线和坐标，因为它们依赖用户当前位置和路线服务。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CinemaResponse(
            String cinemaId,
            String name,
            String cityCode,
            String area,
            String address) {
    }

    /** 影院详情只描述影院静态内容，来源字段帮助前端展示 Demo 标识。 */
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

    /** 列表统一在此封装来源和时效，records 只包含页面渲染字段。 */
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

    /** 内部映射值，防止分页和详情 DTO 分别转换时出现字段差异。 */
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
