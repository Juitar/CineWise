package com.miaoyu.ticket.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CinemaDistanceSelectorTest {
    @Test
    void excludesOverLimitAndKeepsNearestCinema() {
        var origin = new CinemaDistanceSelector.Coordinate(new BigDecimal("112.938814"), new BigDecimal("28.228209"));
        var nearest = new CinemaDistanceSelector.CoordinateCinema(1L, new BigDecimal("112.939000"), new BigDecimal("28.229000"));
        var distant = new CinemaDistanceSelector.CoordinateCinema(2L, new BigDecimal("113.100000"), new BigDecimal("28.400000"));
        assertThat(CinemaDistanceSelector.selectNearest(origin, List.of(distant, nearest), 3000))
                .extracting(CinemaDistanceSelector.DistanceCinema::cinemaId).containsExactly(1L);
    }
}
