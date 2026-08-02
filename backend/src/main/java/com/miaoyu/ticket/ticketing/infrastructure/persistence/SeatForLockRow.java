package com.miaoyu.ticket.ticketing.infrastructure.persistence;

/** 建单事务按主键升序读取的座位锁定投影。 */
public record SeatForLockRow(
        long seatId,
        String rowNo,
        String seatNo,
        String status,
        int version) {
}
