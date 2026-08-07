package com.miaoyu.ticket.travel.infrastructure.persistence;

import java.time.LocalDateTime;

/** 仅包含首次创建任务所需字段，防止后续更新意外覆盖任务状态或版本。 */
public record TravelTaskInsertRow(
        long id,
        String taskId,
        String paymentEventId,
        long userId,
        long orderId,
        long showId,
        Long cinemaId,
        String cinemaArea,
        LocalDateTime startAt,
        LocalDateTime triggerAt,
        long orderVersion,
        LocalDateTime createdAt) {
}
