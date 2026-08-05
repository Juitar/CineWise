package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;

/** D 推荐场景使用的批量场次查询条件；城市解析在调用方完成。 */
public record SaleableShowBatchQuery(
        LocalDate date,
        Collection<Long> cinemaIds,
        LocalTime timeFrom,
        LocalTime timeTo,
        Integer limit) {
}
