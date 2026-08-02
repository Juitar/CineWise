package com.miaoyu.ticket.ticketing.infrastructure.persistence;

/** 按稳定行号、座号排序的单个座位持久化投影。 */
public record SeatQueryRow(
        long seatId,
        String rowNo,
        String seatNo,
        String seatLabel,
        String status,
        int version) {
}
