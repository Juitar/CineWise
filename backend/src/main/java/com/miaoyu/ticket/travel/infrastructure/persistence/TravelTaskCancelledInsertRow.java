package com.miaoyu.ticket.travel.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * 退款先到时写入 CANCELLED 墓碑的持久化行对象。
 *
 * <p>该对象仅在 infrastructure 内部使用，避免将表字段泄露给 A 的退款补偿调用。</p>
 */
public record TravelTaskCancelledInsertRow(
        long id,
        String taskId,
        String invalidationEventId,
        long userId,
        long orderId,
        long showId,
        String cinemaArea,
        LocalDateTime startAt,
        LocalDateTime triggerAt,
        long orderVersion,
        LocalDateTime closedAt) {
}
