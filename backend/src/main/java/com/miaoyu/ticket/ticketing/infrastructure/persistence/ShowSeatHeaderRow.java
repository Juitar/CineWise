package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import java.time.LocalDateTime;

/** 校验场次并构造座位图头部的持久化投影。 */
public record ShowSeatHeaderRow(
        long showId,
        long auditoriumId,
        String auditoriumName,
        int rowCount,
        int seatCount,
        int availableSeatCount,
        String status,
        LocalDateTime startTime,
        int version,
        LocalDateTime updatedAt) {
}
