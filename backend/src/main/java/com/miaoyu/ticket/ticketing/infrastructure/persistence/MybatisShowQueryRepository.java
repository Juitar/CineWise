package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import com.miaoyu.ticket.ticketing.application.ShowQueryRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 将 MyBatis 持久化投影显式映射为票务应用层快照。 */
@Repository
public class MybatisShowQueryRepository implements ShowQueryRepository {

    private final TicketingQueryMapper mapper;

    public MybatisShowQueryRepository(TicketingQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ShowSnapshot> findSaleableShows(QueryCriteria criteria) {
        return mapper.findSaleableShows(criteria).stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public List<ShowSnapshot> findSaleableShowsByCinemaIds(BatchQueryCriteria criteria) {
        return mapper.findSaleableShowsByCinemaIds(criteria).stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public Optional<ShowSeatHeader> findShowSeatHeader(long showId) {
        ShowSeatHeaderRow row = mapper.findShowSeatHeader(showId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new ShowSeatHeader(
                row.showId(),
                row.auditoriumId(),
                row.auditoriumName(),
                row.rowCount(),
                row.seatCount(),
                row.availableSeatCount(),
                row.status(),
                row.startTime(),
                row.version(),
                row.updatedAt()));
    }

    @Override
    public Optional<ShowContext> findShowContext(long showId) {
        ShowContextRow row = mapper.findShowContext(showId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new ShowContext(row.showId(), row.movieId(), row.cinemaId(), row.startTime()));
    }

    @Override
    public List<SeatSnapshot> findSeats(long showId) {
        return mapper.findSeats(showId).stream()
                .map(row -> new SeatSnapshot(
                        row.seatId(),
                        row.rowNo(),
                        row.seatNo(),
                        row.seatLabel(),
                        row.status(),
                        row.version()))
                .toList();
    }

    private ShowSnapshot toSnapshot(ShowQueryRow row) {
        return new ShowSnapshot(
                row.showId(),
                row.movieId(),
                row.cinemaId(),
                row.auditoriumId(),
                row.auditoriumName(),
                row.startTime(),
                row.endTime(),
                row.languageVersion(),
                row.basePrice(),
                row.availableSeatCount(),
                row.status(),
                row.dataType(),
                row.source(),
                row.version(),
                row.updatedAt());
    }
}
