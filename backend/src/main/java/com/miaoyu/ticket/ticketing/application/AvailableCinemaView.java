package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;

/** 影片购票入口展示的最小影院摘要。 */
public record AvailableCinemaView(long cinemaId, String name, String address, int availableShowCount,
        LocalDateTime nearestStartTime, String contentSource, LocalDateTime contentDataTime,
        LocalDateTime contentExpiresAt, boolean contentExpired, String scheduleSource, LocalDateTime scheduleDataTime) {
}
