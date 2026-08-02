package com.miaoyu.ticket.order.application;

import java.time.LocalDate;

/** 本人订单列表的可选白名单筛选与分页输入。 */
public record OrderListQuery(
        String orderNo,
        String status,
        LocalDate dateFrom,
        LocalDate dateTo,
        Integer page,
        Integer size) {
}
