package com.miaoyu.ticket.geo.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 浏览器坐标统一由 D 规范到 6 位小数，页面不需要自行截断。 */
class BrowserUserLocationAdapterTest {

    @Test
    void givenOverPreciseBrowserCoordinate_whenResolving_thenRoundsHalfUpToSixDigits() {
        var point = new BrowserUserLocationAdapter().fromBrowser(
                new BigDecimal("112.9388146"), new BigDecimal("28.2282085"));

        assertThat(point.longitude()).isEqualByComparingTo("112.938815");
        assertThat(point.latitude()).isEqualByComparingTo("28.228209");
    }

    @Test
    void givenRawCoordinateOutsideRange_whenResolving_thenRejectsBeforeRounding() {
        var adapter = new BrowserUserLocationAdapter();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.fromBrowser(
                new BigDecimal("180.0000004"), new BigDecimal("0")))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.fromBrowser(
                new BigDecimal("0"), new BigDecimal("-90.0000004")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
