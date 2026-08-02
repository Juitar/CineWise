package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 有界场次列表查询的持久化投影。 */
public record ShowQueryRow(
        long showId,
        long movieId,
        long cinemaId,
        long auditoriumId,
        String auditoriumName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String languageVersion,
        BigDecimal basePrice,
        int availableSeatCount,
        String status,
        String dataType,
        int version,
        LocalDateTime updatedAt) {
}
