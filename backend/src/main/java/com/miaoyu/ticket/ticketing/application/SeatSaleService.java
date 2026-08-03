package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** 将本订单拥有的LOCKED座位批量条件迁移为SOLD。 */
@Service
public class SeatSaleService {

    private final SeatSaleRepository repository;

    public SeatSaleService(SeatSaleRepository repository) {
        this.repository = repository;
    }

    /** SQL归属条件防止支付售出其他订单或已经释放的座位。 */
    public int sellLockedSeats(String orderNo, LocalDateTime soldAt) {
        return repository.sellLockedSeats(orderNo, soldAt);
    }
}
