package com.miaoyu.ticket.ticketing.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 单个可售场次的应用层只读模型。 */
public record ShowSummaryView(
        long showId,
        long movieId,
        long cinemaId,
        String cinemaName,
        long auditoriumId,
        String auditoriumName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String languageVersion,
        BigDecimal basePrice,
        int availableSeatCount,
        String status,
        String dataType,
        int stateVersion,
        LocalDateTime updatedAt) {
}
