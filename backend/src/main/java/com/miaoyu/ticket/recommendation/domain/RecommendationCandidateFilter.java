package com.miaoyu.ticket.recommendation.domain;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/** 推荐评分前的硬过滤器，任何不满足当前明确条件的候选都不能进入排序。 */
public final class RecommendationCandidateFilter {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private RecommendationCandidateFilter() { }

    /** 过滤仅依据当前请求和 A 的票务事实，不读取画像或尝试放宽用户条件。 */
    public static List<RankedRecommendationCandidate> filter(
            List<RankedRecommendationCandidate> candidates, RecommendationConstraints constraints, Clock clock) {
        Objects.requireNonNull(candidates, "candidates 不能为空");
        Objects.requireNonNull(constraints, "constraints 不能为空");
        Objects.requireNonNull(clock, "clock 不能为空");
        return candidates.stream().filter(candidate -> matches(candidate, constraints, clock)).toList();
    }

    private static boolean matches(
            RankedRecommendationCandidate candidate, RecommendationConstraints constraints, Clock clock) {
        // 过期票务快照不能靠较高评分重新进入可购推荐。
        if (!candidate.expiresAt().isAfter(clock.instant())) {
            return false;
        }
        LocalDateTime start = LocalDateTime.ofInstant(candidate.startTime(), BUSINESS_ZONE);
        LocalDateTime end = LocalDateTime.ofInstant(candidate.endTime(), BUSINESS_ZONE);
        if (!start.toLocalDate().equals(constraints.date())) {
            return false;
        }
        if (constraints.movieId() != null && !constraints.movieId().equals(candidate.movieId())) {
            return false;
        }
        if (constraints.cinemaId() != null && !constraints.cinemaId().equals(candidate.cinemaId())) {
            return false;
        }
        if (candidate.availableSeatCount() < constraints.ticketCount()) {
            return false;
        }
        if (!constraints.genres().isEmpty()
                && candidate.genres().stream().noneMatch(constraints.genres()::contains)) {
            return false;
        }
        if (candidate.genres().stream().anyMatch(constraints.excludedGenres()::contains)) {
            return false;
        }
        if (constraints.timeFrom() != null && (start.toLocalTime().isBefore(constraints.timeFrom())
                || start.toLocalTime().isAfter(constraints.timeTo()))) {
            return false;
        }
        if (constraints.latestEndTime() != null && end.toLocalTime().isAfter(constraints.latestEndTime())) {
            return false;
        }
        return constraints.budget() == null || candidate.price().compareTo(constraints.budget()) <= 0;
    }
}
