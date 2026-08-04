package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A 的可售日期只读仓储端口；实现必须在数据库侧完成有界日期聚合。
 *
 * <p>端口不暴露座位或内容投影，防止调用方把日期摘要误当作库存或影片影院存在性证明。</p>
 */
public interface AvailableDateQueryRepository {

    /**
     * 返回匹配影片和影院的日期统计，按日期升序排列。
     * startsAfter和startsBefore共同形成开区间，防止已开场或第八天场次混入结果。
     */
    List<AvailableDateSnapshot> findAvailableDates(QueryCriteria criteria);

    /** 查询条件只承载A拥有的排期筛选，不携带内容模块展示字段。 */
    record QueryCriteria(
            long movieId,
            long cinemaId,
            LocalDateTime startsAfter,
            LocalDateTime startsBefore) {
    }

    /** Repository快照与REST DTO分离，避免持久化投影穿透API边界。 */
    record AvailableDateSnapshot(LocalDate date, int showCount) {
    }
}
