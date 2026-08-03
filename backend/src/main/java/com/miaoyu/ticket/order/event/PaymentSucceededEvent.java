package com.miaoyu.ticket.order.event;

import java.time.OffsetDateTime;

/** A向D发布的支付成功冻结契约，不包含模拟密码或完整订单明细。 */
public record PaymentSucceededEvent(
        String eventId,
        String orderId,
        String showId,
        String userId,
        String cinemaArea,
        OffsetDateTime startAt,
        long orderVersion,
        OffsetDateTime occurredAt) {
}
