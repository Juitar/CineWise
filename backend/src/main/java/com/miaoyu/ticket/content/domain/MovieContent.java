package com.miaoyu.ticket.content.domain;

import java.math.BigDecimal;
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
        Long movieId,
        String sourceMovieId,
        String title,
        String genresJson,
        int durationMinutes,
        BigDecimal rating) implements ContentItem {

    /** 资源目录不保存数据库主键；按实际主键查询并回退时才由 Provider 填入。 */
    public MovieContent(String sourceMovieId, String title, String genresJson, int durationMinutes, BigDecimal rating) {
        this(null, sourceMovieId, title, genresJson, durationMinutes, rating);
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
}
