package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** 只释放仍由指定订单拥有的LOCKED座位。 */
@Service
public class SeatReleaseService {

    private final SeatReleaseRepository repository;

    public SeatReleaseService(SeatReleaseRepository repository) {
        this.repository = repository;
    }

    /**
     * SQL同时检查`status=LOCKED`和`lock_order_no`，
     * 防止旧订单释放已经转移给其他交易的座位。
     */
    public int releaseLockedSeats(String orderNo, LocalDateTime updatedAt) {
        return repository.releaseLockedSeats(orderNo, updatedAt);
    }
}
