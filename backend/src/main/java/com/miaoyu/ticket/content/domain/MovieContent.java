package com.miaoyu.ticket.content.domain;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * 影片的标准化内容，不包含场次、票价或库存。
 *
 * <p>`sourceMovieId` 是 Mock 数据和未来 Provider 的跨环境身份；内部数据库 ID 由内容种子
 * 首次写入时生成，不能作为 Provider 或资源文件的固定字段。</p>
 *
 * <p>类型、时长和评分属于内容事实；可购时间、价格和余座属于 A 的票务事实，禁止加入本模型。</p>
 */
public record MovieContent(
        // 内部 ID 只在持久化后存在；Provider 不能指定或替代它。
        Long movieId,
        // 外部 ID 用于来源追溯，不能作为对前端公开的影片 ID。
        String sourceMovieId,
        String title,
        String genresJson,
        int durationMinutes,
        BigDecimal rating,
        // 外链图片会被浏览器请求，因此只接受已经规范化的 HTTPS 绝对地址。
        String posterUrl,
        // 简介是可选短文本，影评、评论和富文本不属于内容模型。
        String summary,
        // 上映日期和状态只描述影片资料，不代表票务模块有可售场次。
        String releaseDate,
        String releaseStatus) implements ContentItem {

    /** 资源目录不保存数据库主键；按实际主键查询并回退时才由 Provider 填入。 */
    public MovieContent(String sourceMovieId, String title, String genresJson, int durationMinutes, BigDecimal rating) {
        this(null, sourceMovieId, title, genresJson, durationMinutes, rating, null, null, null, null);
    }

    /**
     * 兼容已有快照、Demo 和测试的影片构造方式；新增资料字段均可缺失，不能阻断既有基础目录读取。
     */
    public MovieContent(Long movieId, String sourceMovieId, String title, String genresJson,
                        int durationMinutes, BigDecimal rating) {
        this(movieId, sourceMovieId, title, genresJson, durationMinutes, rating, null, null, null, null);
    }

    /**
     * 固定 Demo 数据必须完整，提前拒绝空来源 ID 和无效时长，避免无法幂等的内容进入种子。
     */
    public MovieContent {
        if (movieId != null && movieId <= 0L) {
            throw new IllegalArgumentException("movieId must be positive");
        }
        sourceMovieId = requireText(sourceMovieId, "sourceMovieId");
        title = requireText(title, "title");
        genresJson = requireText(genresJson, "genresJson");
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("durationMinutes must be positive");
        }
        rating = Objects.requireNonNull(rating, "rating must not be null");
        posterUrl = normalizeHttpsUrl(posterUrl);
        summary = normalizeOptionalText(summary);
        releaseDate = normalizeReleaseDate(releaseDate);
        releaseStatus = normalizeReleaseStatus(releaseStatus);
    }

    /** 影片模型只对应影片资源类型，供统一查询和缓存端口选择正确处理分支。 */
    @Override
    public ContentResourceType resourceType() {
        return ContentResourceType.MOVIE;
    }

    /** 统一规范化文本字段，避免只含空白的内容进入快照和演示目录。 */
    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    /**
     * 海报会直接交给浏览器加载，领域模型再校验一次 HTTPS，避免有其他 Provider 绕过 Mapper 写入 HTTP 地址。
     */
    private static String normalizeHttpsUrl(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("posterUrl must be an absolute HTTPS URL");
            }
            return uri.normalize().toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("posterUrl must be an absolute HTTPS URL", exception);
        }
    }

    /** 可选资料统一去空白；Provider 缺字段时使用 null，不能伪造内容填满页面。 */
    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /** 上游日期只在符合 C 约定的完整日期格式时保留，异常值不能进入快照和公开接口。 */
    private static String normalizeReleaseDate(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDate.parse(normalized).toString();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /** 状态是公开 DTO 枚举，不得把 Provider 任意文本透传给缓存、快照或前端。 */
    private static String normalizeReleaseStatus(String value) {
        String normalized = normalizeOptionalText(value);
        return "NOW_SHOWING".equals(normalized) || "COMING_SOON".equals(normalized) ? normalized : null;
    }
}
