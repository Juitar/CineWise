package com.miaoyu.ticket.ticketing.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 退票资格和替代场次使用的票务只读端口。
 *
 * <ul>
 *   <li>原场次上下文只暴露影片ID和开场时间；</li>
 *   <li>替代查询只读取A拥有的排期、票价和座位事实；</li>
 *   <li>日期和当前时间双重边界由调用方明确传入；</li>
 *   <li>仓储负责ON_SALE过滤、原场次排除和稳定排序。</li>
 * </ul>
 */
public interface RefundShowRepository {

    /** 原场次决定退票截止时间和替代查询使用的影片ID。 */
    Optional<RefundShowContext> findRefundShowContext(long showId);

    /** 查询同影片、排除原场次且仍可售的有界候选。 */
    List<AlternativeShowSnapshot> findAlternativeShows(AlternativeShowCriteria criteria);

    record RefundShowContext(long showId, long movieId, LocalDateTime startTime) {
    }

    record AlternativeShowCriteria(
            long movieId,
            long excludedShowId,
            LocalDateTime startsAfter,
            LocalDateTime startsAtOrAfter,
            LocalDateTime startsBefore) {
    }

    /**
     * 查询时的替代场次快照。
     *
     * <p>availableSeatCount只用于展示，后续建单仍需重新读取座位并执行条件锁定。</p>
     */
    record AlternativeShowSnapshot(
            long showId,
            long cinemaId,
            LocalDateTime startTime,
            BigDecimal basePrice,
            String status,
            int availableSeatCount) {
    }
}
