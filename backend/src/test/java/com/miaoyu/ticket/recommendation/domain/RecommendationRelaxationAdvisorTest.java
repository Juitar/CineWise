package com.miaoyu.ticket.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationRelaxationAdvisorTest {
    @Test
    void suggestsBudgetOnlyWhenRemovingBudgetRestoresCandidate() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T10:00:00Z"), ZoneOffset.UTC);
        RecommendationConstraints constraints = new RecommendationConstraints("430100", LocalDate.of(2026, 8, 6),
                1, null, null, List.of(), null, null, null, new BigDecimal("30.00"), List.of());
        Instant start = Instant.parse("2026-08-06T12:00:00Z");
        RankedRecommendationCandidate candidate = new RankedRecommendationCandidate("10", "20", "30",
                new BigDecimal("31.00"), start, start.plusSeconds(7200), List.of("喜剧"), new BigDecimal("8.0"),
                "TICKETING:MOCK", Instant.parse("2026-08-06T10:00:00Z"), start);
        assertThat(RecommendationRelaxationAdvisor.suggest(List.of(candidate), constraints, clock))
                .hasValueSatisfying(value -> assertThat(value.factor()).isEqualTo(RelaxationSuggestion.Factor.BUDGET));
    }
}
