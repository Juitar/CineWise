package com.miaoyu.ticket.travel.application;

import java.time.OffsetDateTime;

/** 不含精确起点和路线几何的路线响应摘要。 */
public record BasicRouteResult(
        String provider, String travelMode, int durationMinutes, OffsetDateTime suggestedDepartureAt,
        String source, OffsetDateTime dataTime, OffsetDateTime expiresAt, boolean isExpired,
        boolean degraded, String fallbackType) {
}
