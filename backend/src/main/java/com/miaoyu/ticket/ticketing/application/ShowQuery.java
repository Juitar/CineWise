package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDate;
import java.time.LocalTime;

/** 固定影片、固定影院的公开场次查询条件，进入仓储前由应用服务校验。 */
public record ShowQuery(long movieId, long cinemaId, LocalDate date, LocalTime timeFrom, LocalTime timeTo) {
}
