package com.miaoyu.ticket.content.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 影院的标准化内容，不包含影厅、场次、座位或库存。
 *
 * <p>`sourceCinemaId` 是跨环境身份，城市、行政区和地址用于内容展示和后续出行建议；它们
 * 不代表 A 的影厅或场次归属。</p>
 *
 * <p>经纬度只保留影院静态位置，后续路线请求的用户精确起点不能写入该模型或内容快照。</p>
 *
 * <p>影厅名称和场次时间由票务模块维护，内容变化不能覆盖这些票务数据。</p>
 */
public record CinemaContent(
        Long cinemaId,
        String sourceCinemaId,
        String name,
        String cityCode,
        String area,
        String address,
        BigDecimal longitude,
        BigDecimal latitude) implements ContentItem {

    /** 资源目录不保存数据库主键；按实际主键查询并回退时才由 Provider 填入。 */
    public CinemaContent(String sourceCinemaId, String name, String cityCode, String area, String address,
                         BigDecimal longitude, BigDecimal latitude) {
        this(null, sourceCinemaId, name, cityCode, area, address, longitude, latitude);
    }

    /**
     * 固定 Demo 影院必须有可复用的来源 ID 和完整地址字段，否则不能作为初始化或回退数据。
     */
    public CinemaContent {
        if (cinemaId != null && cinemaId <= 0L) {
            throw new IllegalArgumentException("cinemaId must be positive");
        }
        sourceCinemaId = requireText(sourceCinemaId, "sourceCinemaId");
        name = requireText(name, "name");
        cityCode = requireText(cityCode, "cityCode");
        area = requireText(area, "area");
        address = requireText(address, "address");
        longitude = Objects.requireNonNull(longitude, "longitude must not be null");
        latitude = Objects.requireNonNull(latitude, "latitude must not be null");
    }

    /** 影院模型只对应影院资源类型，避免 Provider 混用影片和影院结果。 */
    @Override
    public ContentResourceType resourceType() {
        return ContentResourceType.CINEMA;
    }

    /** 统一规范化文本字段，避免空白地址或城市生成不可查询的缓存与快照。 */
    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
