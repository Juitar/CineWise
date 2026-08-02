package com.miaoyu.ticket.order.application;

import java.util.List;

/** 页面建单用例的类型化输入；用户和金额不属于调用方可信参数。 */
public record CreateOrderCommand(
        long showId,
        List<Long> seatIds,
        String clientRequestId,
        String idempotencyKey) {
}
