package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;

/** 支付事务将本订单仍锁定的座位条件更新为已售。 */
public interface SeatSaleRepository {

    int sellLockedSeats(String orderNo, LocalDateTime soldAt);
}
