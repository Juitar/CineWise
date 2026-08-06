package com.miaoyu.ticket.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationCandidateFilterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-06T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void filtersExcludedGenreAndOverBudgetCandidateBeforeScoring() {
        RecommendationConstraints constraints = new RecommendationConstraints("430100", LocalDate.of(2026, 8, 6), 2,
                null, null, List.of(), null, null, null, new BigDecimal("50.00"), List.of("恐怖"));
        RankedRecommendationCandidate horror = candidate("1", new BigDecimal("40.00"), List.of("恐怖"));
        RankedRecommendationCandidate expensive = candidate("2", new BigDecimal("51.00"), List.of("喜剧"));
        assertThat(RecommendationCandidateFilter.filter(List.of(horror, expensive), constraints, CLOCK)).isEmpty();
    }

    private RankedRecommendationCandidate candidate(String showId, BigDecimal price, List<String> genres) {
        Instant start = Instant.parse("2026-08-06T12:00:00Z");
        return new RankedRecommendationCandidate("10", "20", showId, price, start, start.plusSeconds(7200), genres,
                new BigDecimal("8.0"), "TICKETING:MOCK", Instant.parse("2026-08-06T10:00:00Z"), start);
    }
}
