package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import com.miaoyu.ticket.ticketing.application.AvailableDateQueryRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 将MyBatis日期统计投影转换为A公开应用层快照。 */
@Repository
public class MybatisAvailableDateQueryRepository implements AvailableDateQueryRepository {

    private final TicketingQueryMapper mapper;

    public MybatisAvailableDateQueryRepository(TicketingQueryMapper mapper) {
        this.mapper = mapper;
    }

    /** 显式映射可隔离SQL别名或持久化类型变化，避免投影直接泄漏到Application层。 */
    @Override
    public List<AvailableDateSnapshot> findAvailableDates(QueryCriteria criteria) {
        return mapper.findAvailableDates(criteria).stream()
                .map(row -> new AvailableDateSnapshot(row.showDate(), row.showCount()))
                .toList();
    }
}
