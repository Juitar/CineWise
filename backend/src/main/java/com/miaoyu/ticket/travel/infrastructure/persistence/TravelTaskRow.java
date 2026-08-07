package com.miaoyu.ticket.travel.infrastructure.persistence;

import java.time.LocalDateTime;

/** MyBatis 读取 travel_task 时使用的持久化行对象，不离开 infrastructure 包。 */
public record TravelTaskRow(
        long id,
        String taskId,
        long userId,
        long orderId,
        long showId,
        Long cinemaId,
        String cinemaArea,
        LocalDateTime startAt,
        LocalDateTime triggerAt,
        long orderVersion,
        long version,
        String status,
        LocalDateTime closedAt,
        LocalDateTime updatedAt) {
}
