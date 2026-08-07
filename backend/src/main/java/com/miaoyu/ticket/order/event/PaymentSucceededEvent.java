package com.miaoyu.ticket.order.event;

import java.time.OffsetDateTime;

/**
 * A向D发布的支付成功冻结契约，不包含模拟密码或完整订单明细。
 *
 * <p>movieId来自同一A权威场次且允许为空；cinemaId是A权威场次关联的正十进制业务ID。
 * 事件边界保持字符串，避免消费者把雪花ID降级为数值。</p>
 */
public record PaymentSucceededEvent(
        String eventId,
        String orderId,
        String showId,
        String movieId,
        String cinemaId,
        String userId,
        String cinemaArea,
        OffsetDateTime startAt,
        long orderVersion,
        OffsetDateTime occurredAt) {
}
