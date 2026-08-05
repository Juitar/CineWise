package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;
import java.util.List;

/** A 的按影院可售影片聚合仓储，所有时间和状态过滤必须在数据库侧完成。 */
public interface AvailableMovieQueryRepository {

    /** 查询指定影院未来窗口内的影片聚合，结果按最近开场时间和影片 ID 稳定排序。 */
    List<AvailableMovieSnapshot> findAvailableMovies(QueryCriteria criteria);

    /** 查询条件不携带内容展示字段，避免票务仓储依赖 D 的内容模型。 */
    record QueryCriteria(long cinemaId, LocalDateTime startsAfter, LocalDateTime startsBefore) {
    }

    /** 排期来源和更新时间来自 A 的 movie_show 快照，不表示影片基础资料来源。 */
    record AvailableMovieSnapshot(
            long movieId,
            int showCount,
            LocalDateTime nearestStartTime,
            String dataSource,
            LocalDateTime dataTime) {
    }
}
