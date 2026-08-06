package com.miaoyu.ticket.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationPlanRankerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-06T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsDistinctPlansAndUsesNextLowestPriceWhenFirstChoiceIsAlreadyUsed() {
        List<RecommendationPlan> plans = RecommendationPlanRanker.rank(List.of(
                candidate("1", "30.00", "7.0", "2026-08-06T11:00:00Z"),
                candidate("2", "20.00", "8.0", "2026-08-06T12:00:00Z"),
                candidate("3", "40.00", "9.0", "2026-08-06T13:00:00Z")),
                new RecommendationConstraints("430100", LocalDate.of(2026, 8, 6), 1, null, null, List.of(),
                        LocalTime.of(19, 0), LocalTime.of(23, 0), null, null, List.of()), CLOCK);

        assertThat(plans).hasSize(3);
        assertThat(plans).extracting(RecommendationPlan::showId).doesNotHaveDuplicates();
        assertThat(plans).filteredOn(plan -> plan.planType() == RecommendationPlan.PlanType.LOW_PRICE)
                .extracting(RecommendationPlan::showId).containsExactly("1");
    }

    @Test
    void createsNearestPlanFromDistanceContextResult() {
        RankedRecommendationCandidate first = candidate("1", "30.00", "8.0", "2026-08-06T11:00:00Z");
        RankedRecommendationCandidate second = new RankedRecommendationCandidate(
                "10", "21", "2", new BigDecimal("30.00"),
                Instant.parse("2026-08-06T11:00:00Z"), Instant.parse("2026-08-06T13:00:00Z"), List.of("喜剧"),
                new BigDecimal("8.0"), "TICKETING:MOCK", Instant.parse("2026-08-06T10:00:00Z"),
                Instant.parse("2026-08-06T12:00:00Z"));
        RecommendationPlan plan = RecommendationPlanRanker.nearest(
                List.of(first, second), java.util.Map.of("20", 1800, "21", 900),
                new RecommendationConstraints("430100", LocalDate.of(2026, 8, 6), 1, null, null,
                        List.of(), null, null, null, null, List.of()), CLOCK);
        assertThat(plan.planType()).isEqualTo(RecommendationPlan.PlanType.NEAREST);
        assertThat(plan.cinemaId()).isEqualTo("21");
    }

    @Test
    void keepsTheSamePlansForRepeatedQueriesAndExcludesExpiredCandidates() {
        RecommendationConstraints constraints = new RecommendationConstraints("430100", LocalDate.of(2026, 8, 6),
                1, null, null, List.of("喜剧"), null, null, null, null, List.of());
        RankedRecommendationCandidate eligible = candidate("1", "30.00", "8.0", "2026-08-06T11:00:00Z");
        Instant expiredStart = Instant.parse("2026-08-06T11:30:00Z");
        RankedRecommendationCandidate expired = new RankedRecommendationCandidate(
                "10", "20", "2", new BigDecimal("10.00"),
                expiredStart, expiredStart.plusSeconds(7200), List.of("喜剧"), new BigDecimal("9.0"), "TICKETING:MOCK",
                Instant.parse("2026-08-06T09:00:00Z"), Instant.parse("2026-08-06T10:00:00Z"));

        List<RecommendationPlan> first = RecommendationPlanRanker.rank(List.of(eligible, expired), constraints, CLOCK);
        List<RecommendationPlan> second = RecommendationPlanRanker.rank(List.of(eligible, expired), constraints, CLOCK);

        // 重复查询不能因集合遍历顺序变化生成不同的 showId 或方案顺序。
        assertThat(first).isEqualTo(second);
        // 过期快照即使票价低、评分高，也不能作为可购买推荐返回。
        assertThat(first).extracting(RecommendationPlan::showId).containsOnly("1");
    }

    private RankedRecommendationCandidate candidate(String showId, String price, String rating, String startText) {
        Instant start = Instant.parse(startText);
        return new RankedRecommendationCandidate("10", "20", showId, new BigDecimal(price), start,
                start.plusSeconds(7200), List.of("喜剧"), new BigDecimal(rating), "TICKETING:MOCK",
                Instant.parse("2026-08-06T10:00:00Z"), Instant.parse("2026-08-06T10:01:00Z"));
    }
}
