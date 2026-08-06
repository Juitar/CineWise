package com.miaoyu.ticket.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CinemaDistanceSelectorTest {
    @Test
    void excludesOverLimitAndKeepsNearestCinema() {
        var origin = new CinemaDistanceSelector.Coordinate(
                new BigDecimal("112.938814"), new BigDecimal("28.228209"));
        var nearest = new CinemaDistanceSelector.CoordinateCinema(
                1L, new BigDecimal("112.939000"), new BigDecimal("28.229000"));
        var distant = new CinemaDistanceSelector.CoordinateCinema(
                2L, new BigDecimal("113.100000"), new BigDecimal("28.400000"));
        assertThat(CinemaDistanceSelector.selectNearest(origin, List.of(distant, nearest), 3000))
                .extracting(CinemaDistanceSelector.DistanceCinema::cinemaId).containsExactly(1L);
    }

    @Test
    void excludesCinemaWithOutOfRangeCoordinate() {
        var origin = new CinemaDistanceSelector.Coordinate(
                new BigDecimal("112.938814"), new BigDecimal("28.228209"));
        var valid = new CinemaDistanceSelector.CoordinateCinema(
                1L, new BigDecimal("112.939000"), new BigDecimal("28.229000"));
        // 内容数据的越界坐标不能传给 Haversine，否则会伪造距离并影响附近影院的筛选结果。
        var invalidLongitude = new CinemaDistanceSelector.CoordinateCinema(
                2L, new BigDecimal("180.000001"), new BigDecimal("28.229000"));
        var invalidLatitude = new CinemaDistanceSelector.CoordinateCinema(
                3L, new BigDecimal("112.939000"), new BigDecimal("-90.000001"));

        assertThat(CinemaDistanceSelector.selectNearest(
                origin, List.of(invalidLongitude, valid, invalidLatitude), null))
                .extracting(CinemaDistanceSelector.DistanceCinema::cinemaId).containsExactly(1L);
    }
}
