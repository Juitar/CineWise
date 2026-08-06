package com.miaoyu.ticket.recommendation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 只用本次位置和影院静态坐标计算直线距离，不调用路线服务。 */
public final class CinemaDistanceSelector {
    private static final double EARTH_RADIUS_METERS = 6_371_000D;
    private static final int MAX_NEARBY_CINEMAS = 10;

    private CinemaDistanceSelector() { }

    /** 选出距离上限内的最近影院；缺坐标影院不参与附近排序，但不影响普通城市推荐。 */
    public static List<DistanceCinema> selectNearest(
            Coordinate origin, List<CoordinateCinema> cinemas, Integer maxDistanceMeters) {
        Objects.requireNonNull(origin, "origin 不能为空");
        Objects.requireNonNull(cinemas, "cinemas 不能为空");
        if (maxDistanceMeters != null && maxDistanceMeters <= 0) {
            throw new IllegalArgumentException("maxDistanceMeters 必须大于 0");
        }
        return cinemas.stream().filter(CoordinateCinema::hasCoordinate)
                .map(cinema -> new DistanceCinema(cinema.cinemaId(), distance(origin, cinema)))
                .filter(item -> maxDistanceMeters == null || item.distanceMeters() <= maxDistanceMeters)
                .sorted(Comparator.comparingInt(DistanceCinema::distanceMeters).thenComparingLong(DistanceCinema::cinemaId))
                .limit(MAX_NEARBY_CINEMAS).toList();
    }

    private static int distance(Coordinate origin, CoordinateCinema cinema) {
        double latitudeDelta = Math.toRadians(cinema.latitude().doubleValue() - origin.latitude().doubleValue());
        double longitudeDelta = Math.toRadians(cinema.longitude().doubleValue() - origin.longitude().doubleValue());
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(origin.latitude().doubleValue())) * Math.cos(Math.toRadians(cinema.latitude().doubleValue()))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return (int) Math.round(EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    /** 经纬度只在请求内存存在，构造时拒绝越界值。 */
    public record Coordinate(BigDecimal longitude, BigDecimal latitude) {
        public Coordinate {
            longitude = require(longitude, "longitude", new BigDecimal("-180"), new BigDecimal("180"));
            latitude = require(latitude, "latitude", new BigDecimal("-90"), new BigDecimal("90"));
        }
        private static BigDecimal require(BigDecimal value, String name, BigDecimal min, BigDecimal max) {
            if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) throw new IllegalArgumentException(name + " 超出范围");
            return value;
        }
    }
    public record CoordinateCinema(long cinemaId, BigDecimal longitude, BigDecimal latitude) {
        public boolean hasCoordinate() { return cinemaId > 0 && longitude != null && latitude != null; }
    }
    public record DistanceCinema(long cinemaId, int distanceMeters) { }
}
