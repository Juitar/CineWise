package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import com.miaoyu.ticket.ticketing.application.AvailableMovieQueryRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 将数据库聚合投影显式转换为 A 的应用层快照。 */
@Repository
public class MybatisAvailableMovieQueryRepository implements AvailableMovieQueryRepository {

    private final TicketingQueryMapper mapper;

    public MybatisAvailableMovieQueryRepository(TicketingQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AvailableMovieSnapshot> findAvailableMovies(QueryCriteria criteria) {
        return mapper.findAvailableMovies(criteria).stream()
                .map(row -> new AvailableMovieSnapshot(
                        row.movieId(),
                        row.showCount(),
                        row.nearestStartTime(),
                        row.dataSource(),
                        row.dataTime()))
                .toList();
    }
}
