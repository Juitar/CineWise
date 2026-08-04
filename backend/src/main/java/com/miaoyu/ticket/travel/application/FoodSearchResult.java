package com.miaoyu.ticket.travel.application;

import java.time.OffsetDateTime;
import java.util.List;

/** 周边餐饮的可降级只读结果。 */
public record FoodSearchResult(
        List<FoodPoi> candidates, String source, OffsetDateTime dataTime, OffsetDateTime expiresAt,
        boolean isExpired, boolean degraded, String fallbackType) {
}
