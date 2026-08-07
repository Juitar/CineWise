package com.miaoyu.ticket.geo.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 统一位置值对象必须在任何 Provider 调用前拒绝不可信的坐标。 */
class ResolvedGeoPointTest {

    @Test
    void givenLegalDeviceCoordinate_whenCreating_thenAllowsPersonalDistance() {
        ResolvedGeoPoint point = new ResolvedGeoPoint(
                new BigDecimal("112.938814"), new BigDecimal("28.228209"), LocationGranularity.DEVICE);

        assertThatCode(point::requirePersonalDistanceCapability).doesNotThrowAnyException();
    }

    @Test
    void givenMissingOutOfRangeOrOverPreciseCoordinate_whenCreating_thenRejectsIt() {
        assertThatThrownBy(() -> new ResolvedGeoPoint(null, BigDecimal.ZERO, LocationGranularity.DEVICE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResolvedGeoPoint(new BigDecimal("180.000001"), BigDecimal.ZERO,
                LocationGranularity.DEVICE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResolvedGeoPoint(new BigDecimal("112.9388147"), new BigDecimal("28.228209"),
                LocationGranularity.DEVICE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenRegionalRepresentativePoint_whenRequestingPersonalDistance_thenRejectsIt() {
        ResolvedGeoPoint point = new ResolvedGeoPoint(
                new BigDecimal("112.938814"), new BigDecimal("28.228209"), LocationGranularity.CITY);

        assertThatThrownBy(point::requirePersonalDistanceCapability).isInstanceOf(IllegalArgumentException.class);
    }
}
