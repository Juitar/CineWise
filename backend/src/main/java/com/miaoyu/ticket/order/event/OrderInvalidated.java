package com.miaoyu.ticket.order.event;

import java.time.OffsetDateTime;

/**
 * A向D发布的订单失效冻结契约。
 *
 * <p>该事件只携带取消出行任务所需的最小事实，不包含金额、座位、电子票或个人敏感数据。cinemaId与
 * 支付事件使用同一A权威场次来源和正十进制字符串语义。</p>
 */
public record OrderInvalidated(
        String eventId,
        String orderId,
        String showId,
        String cinemaId,
        String userId,
        String cinemaArea,
        OffsetDateTime startAt,
        long orderVersion,
        OffsetDateTime occurredAt,
        String invalidReason) {
}
