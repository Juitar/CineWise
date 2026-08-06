package com.miaoyu.ticket.recommendation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 只用本次位置和影院静态坐标计算直线距离，不调用路线服务。
 *
 * <p>位置坐标只在一次推荐请求中使用，计算结果仅用于缩小后续向票务模块查询的影院范围。
 * 影院内容数据可能缺少坐标或存在脏数据；这两种情况都不能参与附近推荐，避免将错误距离
 * 展示给用户或错误排除可售场次。</p>
 */
public final class CinemaDistanceSelector {
    /** Haversine 公式使用的平均地球半径；此处只提供直线距离，不表示实际出行路线。 */
    private static final double EARTH_RADIUS_METERS = 6_371_000D;
    /** 附近模式最多向票务模块查询十家影院，避免定位请求放大为城市范围的批量场次查询。 */
    private static final int MAX_NEARBY_CINEMAS = 10;
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");

    private CinemaDistanceSelector() { }

    /**
     * 选出距离上限内的最近影院。
     *
     * <p>缺失或越界的影院坐标视为无坐标并排除；普通城市推荐不会调用本方法，因此不会因
     * 内容侧坐标不完整而失去其他可售影院。</p>
     */
    public static List<DistanceCinema> selectNearest(
            Coordinate origin, List<CoordinateCinema> cinemas, Integer maxDistanceMeters) {
        Objects.requireNonNull(origin, "origin 不能为空");
        Objects.requireNonNull(cinemas, "cinemas 不能为空");
        if (maxDistanceMeters != null && maxDistanceMeters <= 0) {
            throw new IllegalArgumentException("maxDistanceMeters 必须大于 0");
        }
        // 先过滤无效静态坐标，再计算距离，避免脏数据进入排序或距离上限判断。
        // 距离相同按影院 ID 排序，保证相同输入得到稳定的十家候选，便于回归验证。
        // limit 必须在距离排序后执行，确保留下的确实是最近十家而不是内容返回顺序中的前十家。
        return cinemas.stream().filter(CoordinateCinema::hasCoordinate)
                .map(cinema -> new DistanceCinema(cinema.cinemaId(), distance(origin, cinema)))
                .filter(item -> maxDistanceMeters == null || item.distanceMeters() <= maxDistanceMeters)
                .sorted(Comparator.comparingInt(DistanceCinema::distanceMeters)
                        .thenComparingLong(DistanceCinema::cinemaId))
                .limit(MAX_NEARBY_CINEMAS).toList();
    }

    private static int distance(Coordinate origin, CoordinateCinema cinema) {
        // Haversine 只使用两点经纬度。这里不接路线服务，避免把一次性定位扩展为持续出行跟踪。
        // origin 已在入口构造时校验，cinema 则已由 hasCoordinate 过滤，因此以下换算不会处理越界值。
        // 计算结果取整为米，既满足距离上限过滤，也让卡片可以明确标注“直线距离约”。
        double latitudeDelta = Math.toRadians(cinema.latitude().doubleValue() - origin.latitude().doubleValue());
        double longitudeDelta = Math.toRadians(
                cinema.longitude().doubleValue() - origin.longitude().doubleValue());
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(origin.latitude().doubleValue()))
                * Math.cos(Math.toRadians(cinema.latitude().doubleValue()))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return (int) Math.round(EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    /** 经纬度只在请求内存存在，构造时拒绝越界值，防止调用方提交无效定位。 */
    public record Coordinate(BigDecimal longitude, BigDecimal latitude) {
        public Coordinate {
            longitude = require(longitude, "longitude", MIN_LONGITUDE, MAX_LONGITUDE);
            latitude = require(latitude, "latitude", MIN_LATITUDE, MAX_LATITUDE);
        }
        private static BigDecimal require(BigDecimal value, String name, BigDecimal min, BigDecimal max) {
            if (!isInRange(value, min, max)) {
                throw new IllegalArgumentException(name + " 超出范围");
            }
            return value;
        }
    }
    /**
     * 内容侧影院坐标允许为空，因为普通推荐不依赖它；但附近推荐必须同时满足 ID 和经纬度范围。
     * 这里返回 false 而不抛异常，让单条脏内容数据只影响自身，不中断整次推荐。
     */
    public record CoordinateCinema(long cinemaId, BigDecimal longitude, BigDecimal latitude) {
        public boolean hasCoordinate() {
            return cinemaId > 0 && isInRange(longitude, MIN_LONGITUDE, MAX_LONGITUDE)
                    && isInRange(latitude, MIN_LATITUDE, MAX_LATITUDE);
        }
    }
    public record DistanceCinema(long cinemaId, int distanceMeters) { }

    /**
     * 内容侧坐标不可信时采用非抛错校验，供候选过滤复用。
     *
     * <p>上传用户坐标需要把非法参数告知调用方，所以 {@link Coordinate} 仍会抛出异常；
     * 影院坐标来自内容资料，单条异常数据只应被跳过，不能导致整个城市推荐失败。</p>
     *
     * <p>范围使用闭区间，允许经度 -180 与 180、纬度 -90 与 90 的合法边界值。</p>
     * <p>用户坐标和影院坐标复用同一组边界，避免两处规则不一致。</p>
     */
    private static boolean isInRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}
