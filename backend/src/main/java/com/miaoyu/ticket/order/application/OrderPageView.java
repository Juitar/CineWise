package com.miaoyu.ticket.order.application;

import java.util.List;

/** 应用层订单分页结果，页码从1开始。 */
public record OrderPageView(long total, int page, int size, List<OrderView> records) {

    public OrderPageView {
        records = List.copyOf(records);
    }
}
