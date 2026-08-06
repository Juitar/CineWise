package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;
import java.util.List;

/** 按影片聚合可售影院的 A 侧只读持久化边界。 */
public interface AvailableCinemaQueryRepository {

    List<AvailableCinemaSnapshot> findAvailableCinemas(QueryCriteria criteria);

    record QueryCriteria(long movieId, LocalDateTime startsAfter, LocalDateTime startsBefore) {
    }

    /** 场次来源只描述 A 的排期快照，内容展示字段由 D 的公开 Port 提供。 */
    record AvailableCinemaSnapshot(
            long cinemaId, int availableShowCount, LocalDateTime nearestStartTime, String scheduleSource,
            LocalDateTime scheduleDataTime) {
    }
}
