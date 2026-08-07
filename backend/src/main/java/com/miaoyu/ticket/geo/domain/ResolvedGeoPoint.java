package com.miaoyu.ticket.geo.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * D 模块内部统一传递的经纬度与位置粒度。
 *
 * <p>坐标在进入业务模块时完成空值、范围和精度校验。它只表示本次请求或已确认的静态影院位置，
 * 不提供序列化、缓存键或日志文本方法，防止调用方随手把精确用户位置写进持久化介质。</p>
 */
public record ResolvedGeoPoint(BigDecimal longitude, BigDecimal latitude, LocationGranularity granularity) {

    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final int MAX_SCALE = 6;

    /**
     * 统一拒绝不可用或精度超限的坐标。
     *
     * <p>六位小数约为亚米到米级展示精度，已足够用于影院附近排序；更高精度会扩大不必要的位置
     * 暴露面。Provider 需要的字符串只能在其 HTTP Adapter 内临时生成。</p>
     */
    public ResolvedGeoPoint {
        longitude = requireCoordinate(longitude, MIN_LONGITUDE, MAX_LONGITUDE, "longitude");
        latitude = requireCoordinate(latitude, MIN_LATITUDE, MAX_LATITUDE, "latitude");
        granularity = Objects.requireNonNull(granularity, "granularity must not be null");
    }

    /** 仅允许可代表个人位置的粒度进入距离与路线计算。 */
    public void requirePersonalDistanceCapability() {
        if (!granularity.suitableForPersonalDistance()) {
            throw new IllegalArgumentException("区域代表点不能用于个人距离或路线计算");
        }
    }

    private static BigDecimal requireCoordinate(
            BigDecimal value, BigDecimal minimum, BigDecimal maximum, String field) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0
                || Math.max(value.stripTrailingZeros().scale(), 0) > MAX_SCALE) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        // 保持调用方传入的数值语义；不在这里做坐标系转换或写入持久化。
        return value;
    }
}
