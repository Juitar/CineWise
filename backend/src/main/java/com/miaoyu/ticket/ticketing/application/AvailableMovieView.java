package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;

/** 影院详情页使用的可售影片摘要；不携带价格、余座或影厅快照。 */
public record AvailableMovieView(
        long movieId,
        String title,
        String posterUrl,
        int showCount,
        LocalDateTime nearestStartTime,
        String contentSource,
        LocalDateTime contentDataTime,
        String scheduleSource,
        LocalDateTime scheduleDataTime) {
}
