package com.miaoyu.ticket.admin.application;

import java.util.List;

/**
 * 管理订单稳定分页结果。
 *
 * <p>页码从1开始，空结果也保留请求页码和页大小；total始终表示全部匹配数，
 * 不等同于当前records数量。</p>
 */
public record AdminOrderPageView(
        long total,
        int page,
        int size,
        List<AdminOrderView> records) {

    public AdminOrderPageView {
        // 防止调用方在响应组装期间修改查询快照。
        records = List.copyOf(records);
    }
}
