package com.miaoyu.ticket.ticketing.api;

import java.time.OffsetDateTime;
import java.util.List;

/** Agent 场次摘要只反映本次读取结果，不能替代购票页和建单事务的重新校验。 */
public record QueryShowsToolResult(List<ShowItem> shows) {

    public QueryShowsToolResult {
        shows = List.copyOf(shows);
    }

    public record ShowItem(
            String showId,
            String movieId,
            String cinemaId,
            String cinemaName,
            String auditoriumId,
            String auditoriumName,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            OffsetDateTime expiresAt,
            String languageVersion,
            String basePrice,
            int availableSeatCount,
            String status,
            String dataType,
            int stateVersion,
            OffsetDateTime updatedAt) {
    }
}
