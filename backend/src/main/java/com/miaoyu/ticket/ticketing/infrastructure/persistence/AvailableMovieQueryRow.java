package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import java.time.LocalDateTime;

/** MyBatis 可售影片聚合投影，只映射 SQL 实际返回字段。 */
public record AvailableMovieQueryRow(
        long movieId,
        int showCount,
        LocalDateTime nearestStartTime,
        String dataSource,
        LocalDateTime dataTime) {
}
