package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import com.miaoyu.ticket.ticketing.application.SeatLockRepository;
import com.miaoyu.ticket.ticketing.application.SeatReleaseRepository;
import com.miaoyu.ticket.ticketing.application.SeatSaleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 将座位锁定持久化投影显式映射为应用层类型。 */
@Repository
public class MybatisSeatLockRepository implements SeatLockRepository, SeatReleaseRepository, SeatSaleRepository {

    private final TicketingSeatLockMapper mapper;

    public MybatisSeatLockRepository(TicketingSeatLockMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ShowForLock> findShow(long showId) {
        ShowForLockRow row = mapper.findShow(showId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new ShowForLock(
                row.showId(),
                row.basePrice(),
                row.status(),
                row.startTime()));
    }

    @Override
    public List<SeatForLock> findSeats(long showId, List<Long> sortedSeatIds) {
        return mapper.findSeats(showId, sortedSeatIds).stream()
                .map(row -> new SeatForLock(
                        row.seatId(),
                        row.rowNo(),
                        row.seatNo(),
                        row.status(),
                        row.version()))
                .toList();
    }

    @Override
    public boolean lockSeat(SeatLockUpdate update) {
        return mapper.lockSeat(update) == 1;
    }

    @Override
    public int releaseLockedSeats(String orderNo, LocalDateTime updatedAt) {
        return mapper.releaseLockedSeats(orderNo, updatedAt);
    }

    @Override
    public int sellLockedSeats(String orderNo, LocalDateTime soldAt) {
        return mapper.sellLockedSeats(orderNo, soldAt);
    }
}
