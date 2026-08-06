package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import java.time.LocalDateTime;

/** MyBatis 可售影院聚合投影，只包含排期事实字段。 */
public record AvailableCinemaQueryRow(long cinemaId, int availableShowCount, LocalDateTime nearestStartTime,
        String scheduleSource, LocalDateTime scheduleDataTime) {
}
