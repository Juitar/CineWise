package com.miaoyu.ticket.recommendation.domain;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/** 无方案时只提出一项可验证的放宽建议，绝不代替用户修改条件。 */
public final class RecommendationRelaxationAdvisor {
    private RecommendationRelaxationAdvisor() { }

    /** 按类型、时间、预算顺序尝试移除单项条件；无有效替代候选则不建议放宽。 */
    public static Optional<RelaxationSuggestion> suggest(
            List<RankedRecommendationCandidate> candidates, RecommendationConstraints constraints, Clock clock) {
        if (!RecommendationCandidateFilter.filter(candidates, constraints, clock).isEmpty()) {
            return Optional.empty();
        }
        if (!constraints.genres().isEmpty() || !constraints.excludedGenres().isEmpty()) {
            RecommendationConstraints relaxed = copy(constraints, List.of(), List.of(), constraints.timeFrom(),
                    constraints.timeTo(), constraints.latestEndTime(), constraints.budget());
            if (!RecommendationCandidateFilter.filter(candidates, relaxed, clock).isEmpty()) {
                return Optional.of(new RelaxationSuggestion(RelaxationSuggestion.Factor.GENRE, "可尝试放宽影片类型条件"));
            }
        }
        if (constraints.timeFrom() != null || constraints.latestEndTime() != null) {
            RecommendationConstraints relaxed = copy(constraints, constraints.genres(), constraints.excludedGenres(),
                    null, null, null, constraints.budget());
            if (!RecommendationCandidateFilter.filter(candidates, relaxed, clock).isEmpty()) {
                return Optional.of(new RelaxationSuggestion(RelaxationSuggestion.Factor.TIME, "可尝试放宽观影时间条件"));
            }
        }
        if (constraints.budget() != null) {
            RecommendationConstraints relaxed = copy(constraints, constraints.genres(), constraints.excludedGenres(),
                    constraints.timeFrom(), constraints.timeTo(), constraints.latestEndTime(), null);
            if (!RecommendationCandidateFilter.filter(candidates, relaxed, clock).isEmpty()) {
                return Optional.of(new RelaxationSuggestion(RelaxationSuggestion.Factor.BUDGET, "可尝试提高预算"));
            }
        }
        return Optional.empty();
    }

    private static RecommendationConstraints copy(RecommendationConstraints source, List<String> genres,
            List<String> excludedGenres, java.time.LocalTime timeFrom, java.time.LocalTime timeTo,
            java.time.LocalTime latestEndTime, java.math.BigDecimal budget) {
        return new RecommendationConstraints(source.cityCode(), source.date(), source.ticketCount(), source.movieId(),
                source.cinemaId(), genres, timeFrom, timeTo, latestEndTime, budget, excludedGenres);
    }
}
