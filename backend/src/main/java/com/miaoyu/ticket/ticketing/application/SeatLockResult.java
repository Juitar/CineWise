package com.miaoyu.ticket.ticketing.application;

import java.math.BigDecimal;
import java.util.List;

/** 已成功锁定的座位快照，供订单模块在同一事务中追加明细。 */
public record SeatLockResult(
        long showId,
        BigDecimal unitPrice,
        List<LockedSeat> seats) {

    public record LockedSeat(long seatId, String rowNo, String seatNo) {
    }
}
