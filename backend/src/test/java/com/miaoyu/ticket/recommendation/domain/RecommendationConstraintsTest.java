package com.miaoyu.ticket.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationConstraintsTest {

    @Test
    void rejectsOnlyOneTimeBoundary() {
        assertThatThrownBy(() -> new RecommendationConstraints("430100", LocalDate.of(2026, 8, 6), 2,
                null, null, List.of("喜剧"), LocalTime.of(19, 0), null, null, new BigDecimal("60.00"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeBudget() {
        assertThatThrownBy(() -> new RecommendationConstraints("430100", LocalDate.of(2026, 8, 6), 2,
                null, null, List.of(), null, null, null, new BigDecimal("-0.01"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
