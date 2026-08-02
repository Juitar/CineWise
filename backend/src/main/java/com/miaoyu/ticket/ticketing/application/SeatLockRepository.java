package com.miaoyu.ticket.ticketing.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 场次校验与座位条件锁定端口，MySQL是库存最终权威。 */
public interface SeatLockRepository {

    Optional<ShowForLock> findShow(long showId);

    List<SeatForLock> findSeats(long showId, List<Long> sortedSeatIds);

    boolean lockSeat(SeatLockUpdate update);

    record ShowForLock(
            long showId,
            BigDecimal basePrice,
            String status,
            LocalDateTime startTime) {
    }

    record SeatForLock(
            long seatId,
            String rowNo,
            String seatNo,
            String status,
            int version) {
    }

    record SeatLockUpdate(
            long seatId,
            long showId,
            int expectedVersion,
            String orderNo,
            LocalDateTime lockExpiresAt,
            LocalDateTime updatedAt) {
    }
}
