package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import com.miaoyu.ticket.ticketing.application.AvailableCinemaQueryRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 显式映射可售影院聚合 SQL，避免将内容字段带入 A 的持久化查询。 */
@Repository
public class MybatisAvailableCinemaQueryRepository implements AvailableCinemaQueryRepository {

    private final TicketingQueryMapper mapper;

    public MybatisAvailableCinemaQueryRepository(TicketingQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long countAvailableCinemas(QueryCriteria criteria) {
        return mapper.countAvailableCinemas(criteria);
    }

    @Override
    public List<AvailableCinemaSnapshot> findAvailableCinemas(QueryCriteria criteria) {
        return mapper.findAvailableCinemas(criteria).stream()
                .map(row -> new AvailableCinemaSnapshot(row.cinemaId(), row.availableShowCount(),
                        row.nearestStartTime(), row.scheduleSource(), row.scheduleDataTime()))
                .toList();
    }
}
