package com.miaoyu.ticket.order.application;

import java.time.OffsetDateTime;

/** 供出行模块读取本人订单最小场次上下文的公开应用端口。 */
public interface TravelOrderSummaryQueryPort {

    /** 只按当前认证用户查询，不接受调用方提供的 userId。 */
    TravelOrderSummary queryMyOrder(String orderId);

    /** 不包含金额、座位、支付、退款或用户隐私字段的跨模块摘要。 */
    record TravelOrderSummary(
            String orderId,
            String orderNo,
            String showId,
            String movieId,
            String cinemaId,
            OffsetDateTime showStartTime) {
    }
}
