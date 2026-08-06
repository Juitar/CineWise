package com.miaoyu.ticket.ticketing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** `available-cinemas` 的公开响应项，内容与排期来源字段保持明确分离。 */
public record AvailableCinemaResponse(
        @Schema(example = "20001") String cinemaId,
        String name,
        String address,
        int availableShowCount,
        OffsetDateTime nearestStartTime,
        String contentSource,
        OffsetDateTime contentDataTime,
        OffsetDateTime contentExpiresAt,
        boolean contentExpired,
        String scheduleSource,
        OffsetDateTime scheduleDataTime) {
}
