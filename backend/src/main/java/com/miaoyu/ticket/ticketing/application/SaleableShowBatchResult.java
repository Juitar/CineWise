package com.miaoyu.ticket.ticketing.application;

import java.util.List;

/** 批量场次查询结果；truncated 明确表示仍存在未返回的匹配记录。 */
public record SaleableShowBatchResult(List<SaleableShowView> shows, boolean truncated) {

    public SaleableShowBatchResult {
        shows = List.copyOf(shows);
    }
}
