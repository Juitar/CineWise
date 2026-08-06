package com.miaoyu.ticket.ticketing.api;

import java.time.LocalDate;
import java.util.List;

/** Agent 可展示的日期摘要；showCount 是排期数量而非库存或锁座承诺。 */
public record QueryAvailableDatesToolResult(List<AvailableDateItem> dates) {

    public QueryAvailableDatesToolResult {
        dates = List.copyOf(dates);
    }

    public record AvailableDateItem(LocalDate date, int showCount) {
    }
}
