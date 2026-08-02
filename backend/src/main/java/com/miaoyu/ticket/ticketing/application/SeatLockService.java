package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;

/** 票务模块公开的场次校验和座位条件锁定能力。 */
@Service
public class SeatLockService {

    private static final int MINIMUM_TICKET_COUNT = 1;
    private static final int MAXIMUM_TICKET_COUNT = 6;
    private static final String ON_SALE = "ON_SALE";

    private final SeatLockRepository repository;
    private final Clock clock;

    public SeatLockService(SeatLockRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 按座位ID升序执行带版本和前置状态的条件更新。
     * 任一座位失败都抛出运行时异常，由上层建单事务回滚已执行的锁。
     */
    public SeatLockResult lockSeats(
            long showId,
            List<Long> sortedSeatIds,
            String orderNo,
            LocalDateTime lockExpiresAt,
            LocalDateTime updatedAt) {
        validateSeatSelection(showId, sortedSeatIds);
        SeatLockRepository.ShowForLock show = repository.findShow(showId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        if (!ON_SALE.equals(show.status()) || !show.startTime().isAfter(now)) {
            throw new BusinessException(TicketingErrorCode.SHOW_NOT_SALEABLE);
        }

        List<SeatLockRepository.SeatForLock> seats = repository.findSeats(showId, sortedSeatIds);
        if (seats.size() != sortedSeatIds.size()) {
            throw new BusinessException(TicketingErrorCode.SEAT_NOT_LOCKABLE);
        }
        for (SeatLockRepository.SeatForLock seat : seats) {
            boolean locked = repository.lockSeat(new SeatLockRepository.SeatLockUpdate(
                    seat.seatId(),
                    showId,
                    seat.version(),
                    orderNo,
                    lockExpiresAt,
                    updatedAt));
            if (!locked) {
                throw new BusinessException(TicketingErrorCode.SEAT_NOT_LOCKABLE);
            }
        }
        return new SeatLockResult(
                show.showId(),
                show.basePrice(),
                seats.stream()
                        .map(seat -> new SeatLockResult.LockedSeat(
                                seat.seatId(),
                                seat.rowNo(),
                                seat.seatNo()))
                        .toList());
    }

    private void validateSeatSelection(long showId, List<Long> sortedSeatIds) {
        if (showId <= 0
                || sortedSeatIds == null
                || sortedSeatIds.size() < MINIMUM_TICKET_COUNT
                || sortedSeatIds.size() > MAXIMUM_TICKET_COUNT
                || sortedSeatIds.stream().anyMatch(seatId -> seatId == null || seatId <= 0)
                || new HashSet<>(sortedSeatIds).size() != sortedSeatIds.size()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }
}
