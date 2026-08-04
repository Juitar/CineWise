package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import java.time.LocalDate;

/** MyBatis日期聚合投影；只承载SQL实际返回的两个字段。 */
public record AvailableDateQueryRow(LocalDate showDate, int showCount) {
}
