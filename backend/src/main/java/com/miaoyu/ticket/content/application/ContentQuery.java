package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.util.Objects;

/**
 * 内容内部查询条件。
 *
 * <p>影片和影院查询统一走该对象，避免 Controller、Provider、缓存和快照各自解释城市、关键字
 * 和内容 ID。这个类型不包含用户、场次、价格、座位或库存，防止内容模块越过 A 的票务边界。</p>
 *
 * <p>调用方至少给出一种定位条件，避免无条件读取全部外部内容或生成无法复用的缓存键。</p>
 */
public record ContentQuery(
        ContentResourceType resourceType,
        Long contentId,
        String cityCode,
        String keyword) {

    /**
     * 在 Application 边界提前拒绝无效条件，使后续 Provider、缓存和快照都不产生副作用。
     */
    public ContentQuery {
        resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
        cityCode = normalize(cityCode);
        keyword = normalize(keyword);
        if (contentId != null && contentId <= 0L) {
            throw new IllegalArgumentException("contentId must be positive");
        }
        if (contentId == null && cityCode == null && keyword == null) {
            throw new IllegalArgumentException("contentId, cityCode or keyword is required");
        }
    }

    /**
     * 空白条件没有检索意义，统一归一为 null，避免同一查询形成多套缓存或快照记录。
     */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
