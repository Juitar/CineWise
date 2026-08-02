package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;

/** 取消和过期共用的座位归属条件释放端口。 */
public interface SeatReleaseRepository {

    int releaseLockedSeats(String orderNo, LocalDateTime updatedAt);
}
