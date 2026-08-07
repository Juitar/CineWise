package com.miaoyu.ticket.agent.application.reply;

import java.time.Instant;
import java.util.List;

/** PLAN_CARD 单个方案的公开展示字段；不含库存、座位、坐标或内部证据。 */
public record RecommendationPlanCardItem(
        String planType, String movieId, String movieName, String cinemaId, String cinemaName, String showId,
        String price, String currency, Instant startTime, String rating, Double score, List<String> reasons,
        String source, Instant dataAt, Instant expiresAt, boolean expired, boolean purchaseEligible,
        Integer distanceMeters) {
    public RecommendationPlanCardItem { reasons = List.copyOf(reasons); }
}
