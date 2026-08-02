package com.miaoyu.ticket.ticketing.api;

import java.time.OffsetDateTime;

/** 已冻结的单个可选场次 REST 响应契约。 */
public record ShowSummaryResponse(
        String showId,
        String movieId,
        String cinemaId,
        String cinemaName,
        String auditoriumId,
        String auditoriumName,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String languageVersion,
        String basePrice,
        int availableSeatCount,
        String status,
        String dataType,
        int stateVersion,
        OffsetDateTime updatedAt) {
}
