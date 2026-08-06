package com.miaoyu.ticket.recommendation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 按固定权重生成三类不重复方案，避免模型或调用方改变排序。 */
public final class RecommendationPlanRanker {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private RecommendationPlanRanker() { }

    /** 评分只使用候选已带的价格、开始时间和内容评分；没有评分时从分母移除。 */
    public static List<RecommendationPlan> rank(
            List<RankedRecommendationCandidate> candidates, RecommendationConstraints constraints, Clock clock) {
        List<RankedRecommendationCandidate> filtered = RecommendationCandidateFilter.filter(candidates, constraints, clock);
        if (filtered.isEmpty()) return List.of();
        BigDecimal min = filtered.stream().map(RankedRecommendationCandidate::price).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = filtered.stream().map(RankedRecommendationCandidate::price).max(BigDecimal::compareTo).orElseThrow();
        Set<String> usedShows = new HashSet<>();
        List<RecommendationPlan> plans = new ArrayList<>();
        for (RecommendationPlan.PlanType type : List.of(RecommendationPlan.PlanType.COMPREHENSIVE,
                RecommendationPlan.PlanType.LOW_PRICE, RecommendationPlan.PlanType.EARLY_TIME)) {
            filtered.stream().map(candidate -> scored(candidate, constraints, min, max, type))
                    .sorted(Comparator.comparingDouble(Scored::score).reversed()
                            .thenComparing(scored -> scored.candidate().price())
                            .thenComparing(scored -> scored.candidate().startTime())
                            .thenComparing(scored -> scored.candidate().showId()))
                    .filter(scored -> usedShows.add(scored.candidate().showId())).findFirst()
                    .ifPresent(scored -> plans.add(toPlan(scored, type)));
        }
        return List.copyOf(plans);
    }

    /** 最近优先只在 D 已取得一次性距离上下文时调用，距离相同再按原有票价和时间稳定排序。 */
    public static RecommendationPlan nearest(List<RankedRecommendationCandidate> candidates,
            java.util.Map<String, Integer> distanceMeters, RecommendationConstraints constraints, Clock clock) {
        return RecommendationCandidateFilter.filter(candidates, constraints, clock).stream()
                .filter(candidate -> distanceMeters.containsKey(candidate.cinemaId()))
                .min(Comparator.comparingInt((RankedRecommendationCandidate candidate) -> distanceMeters.get(candidate.cinemaId()))
                        .thenComparing(RankedRecommendationCandidate::price).thenComparing(RankedRecommendationCandidate::startTime)
                        .thenComparing(RankedRecommendationCandidate::showId))
                .map(candidate -> new RecommendationPlan(RecommendationPlan.PlanType.NEAREST, candidate.movieId(), candidate.cinemaId(),
                        candidate.showId(), candidate.price(), candidate.startTime(), 100D,
                        List.of("直线距离约 " + distanceMeters.get(candidate.cinemaId()) + " 米"),
                        List.of(new RecommendationEvidence("distanceMeters", Integer.toString(distanceMeters.get(candidate.cinemaId())), candidate.source(), candidate.dataAt(), candidate.expiresAt())),
                        candidate.source(), candidate.dataAt(), candidate.expiresAt(), true)).orElse(null);
    }

    private static Scored scored(RankedRecommendationCandidate candidate, RecommendationConstraints constraints,
            BigDecimal min, BigDecimal max, RecommendationPlan.PlanType type) {
        double priceScore = min.compareTo(max) == 0 ? 100D
                : max.subtract(candidate.price()).multiply(BigDecimal.valueOf(100)).divide(max.subtract(min), 6, RoundingMode.HALF_UP).doubleValue();
        double timeScore = timeScore(candidate, constraints);
        double ratingScore = candidate.rating() == null ? Double.NaN : candidate.rating().multiply(BigDecimal.TEN).doubleValue();
        double[] weights = switch (type) { case COMPREHENSIVE -> new double[] { .35, .35, .30 }; case LOW_PRICE -> new double[] { .60, .25, .15 }; case EARLY_TIME -> new double[] { .20, .65, .15 }; case NEAREST -> new double[] { .20, .65, .15 }; };
        double numerator = priceScore * weights[0] + timeScore * weights[1];
        double denominator = weights[0] + weights[1];
        if (!Double.isNaN(ratingScore)) { numerator += ratingScore * weights[2]; denominator += weights[2]; }
        return new Scored(candidate, numerator / denominator);
    }

    private static double timeScore(RankedRecommendationCandidate candidate, RecommendationConstraints constraints) {
        if (constraints.timeFrom() == null) return 100D;
        LocalDateTime start = LocalDateTime.ofInstant(candidate.startTime(), BUSINESS_ZONE);
        long minutes = java.time.Duration.between(constraints.timeFrom(), start.toLocalTime()).toMinutes();
        return Math.max(0D, 100D - Math.max(0L, minutes) / 30D * 15D);
    }

    private static RecommendationPlan toPlan(Scored scored, RecommendationPlan.PlanType type) {
        RankedRecommendationCandidate candidate = scored.candidate();
        return new RecommendationPlan(type, candidate.movieId(), candidate.cinemaId(), candidate.showId(), candidate.price(), candidate.startTime(), scored.score(),
                List.of("符合" + type.name() + "排序"), List.of(new RecommendationEvidence("showtime", candidate.showId(), candidate.source(), candidate.dataAt(), candidate.expiresAt())), candidate.source(), candidate.dataAt(), candidate.expiresAt(), true);
    }
    private record Scored(RankedRecommendationCandidate candidate, double score) { }
}
